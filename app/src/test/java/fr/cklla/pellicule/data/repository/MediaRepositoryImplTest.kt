package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.firestore.FakeFirestoreMediaDataSource
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `UnconfinedTestDispatcher` pour le scope interne du repository : les coroutines qu'il lance
 * (écoute de `currentUser`, mirroring Firestore -> Room) s'exécutent alors de façon synchrone et
 * déterministe dès qu'un `MutableStateFlow` fake change de valeur, sans avoir besoin d'avancer un
 * temps virtuel séparé — le test reste single-thread de bout en bout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryImplTest {

    private lateinit var dao: FakeMediaDao
    private lateinit var firestoreDataSource: FakeFirestoreMediaDataSource
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var repository: MediaRepository

    // 2026-10-04 à midi UTC ; l'horloge ne sert qu'à `setWatchedYear`.
    private val now = millis(2026, 10, 4)
    private val timeSource = object : TimeSource {
        override fun nowMillis() = now
        override val zone = ZoneOffset.UTC
    }

    private fun millis(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private val dune = Media(
        title = "Dune",
        type = MediaType.FILM,
        status = WatchStatus.A_VOIR,
    )

    private fun buildRepository(scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())) {
        repository = MediaRepositoryImpl(
            mediaDao = dao,
            firestoreDataSource = firestoreDataSource,
            authRepository = authRepository,
            timeSource = timeSource,
            repositoryScope = scope,
        )
    }

    @Before
    fun setUp() {
        dao = FakeMediaDao()
        firestoreDataSource = FakeFirestoreMediaDataSource()
        authRepository = FakeAuthRepository()
        buildRepository()
    }

    @Test
    fun `addMedia rend le contenu visible via observeMedia`() = runTest {
        val result = repository.addMedia(dune)

        assertTrue(result is Resource.Success)
        val media = repository.observeMedia().first()
        assertEquals(1, media.size)
        assertEquals("Dune", media.first().title)
    }

    @Test
    fun `updateMedia modifie le statut du contenu observe`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data
        val vu = dune.copy(id = addedId, status = WatchStatus.VU)

        repository.updateMedia(vu)

        val media = repository.observeMedia().first()
        assertEquals(WatchStatus.VU, media.first().status)
    }

    @Test
    fun `updateMedia horodate le passage au statut Vu`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data
        val vu = dune.copy(id = addedId, status = WatchStatus.VU)

        repository.updateMedia(vu)

        val media = repository.observeMedia().first().first()
        assertTrue(media.watchedAt != null)
    }

    @Test
    fun `updateMedia ne reecrit pas la date de visionnage si deja Vu`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data
        repository.updateMedia(dune.copy(id = addedId, status = WatchStatus.VU))
        val firstWatchedAt = repository.observeMedia().first().first().watchedAt

        // Un changement qui laisse le statut à VU (ex. édition de la note) ne doit pas décaler la
        // date de visionnage déjà enregistrée.
        repository.updateMedia(dune.copy(id = addedId, status = WatchStatus.VU, rating = 5))

        val media = repository.observeMedia().first().first()
        assertEquals(firstWatchedAt, media.watchedAt)
    }

    @Test
    fun `updateMedia efface la date de visionnage si le statut quitte Vu`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data
        repository.updateMedia(dune.copy(id = addedId, status = WatchStatus.VU))

        repository.updateMedia(dune.copy(id = addedId, status = WatchStatus.EN_COURS))

        val media = repository.observeMedia().first().first()
        assertEquals(null, media.watchedAt)
    }

    private suspend fun addVu(watchedAt: Long?, releaseYear: Int? = 2020): String {
        val id = "vu-1"
        dao.insert(dune.copy(id = id, status = WatchStatus.VU, releaseYear = releaseYear, watchedAt = watchedAt).toEntity())
        return id
    }

    private suspend fun watchedAtOf(id: String) = repository.observeMediaById(id).first()?.watchedAt

    @Test
    fun `setWatchedYear pose le 1er juillet de l'annee choisie sur un contenu Vu`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))

        val result = repository.setWatchedYear(id, 2024)

        assertTrue(result is Resource.Success)
        assertEquals(millis(2024, 7, 1), watchedAtOf(id))
    }

    @Test
    fun `setWatchedYear donne une annee a un contenu Vu sans date`() = runTest {
        val id = addVu(watchedAt = null)

        repository.setWatchedYear(id, 2025)

        assertEquals(millis(2025, 7, 1), watchedAtOf(id))
    }

    @Test
    fun `setWatchedYear sur l'annee en cours depuis une annee passee donne l'instant present`() = runTest {
        val id = addVu(watchedAt = millis(2024, 7, 1))

        repository.setWatchedYear(id, 2026)

        assertEquals(now, watchedAtOf(id))
    }

    @Test
    fun `setWatchedYear ne fait rien sur un contenu qui n'est pas Vu`() = runTest {
        dao.insert(dune.copy(id = "en-cours", status = WatchStatus.EN_COURS, releaseYear = 2020).toEntity())

        val result = repository.setWatchedYear("en-cours", 2024)

        assertTrue(result is Resource.Success)
        assertNull(watchedAtOf("en-cours"))
        assertTrue(firestoreDataSource.upsertedMedia.isEmpty())
    }

    @Test
    fun `setWatchedYear ne fait rien sur un contenu inconnu`() = runTest {
        val result = repository.setWatchedYear("absent", 2024)

        assertTrue(result is Resource.Success)
        assertTrue(repository.observeMedia().first().isEmpty())
        assertTrue(firestoreDataSource.upsertedMedia.isEmpty())
    }

    @Test
    fun `setWatchedYear refuse une annee future ou anterieure a la sortie`() = runTest {
        val original = millis(2026, 3, 1)
        val id = addVu(watchedAt = original, releaseYear = 2020)

        repository.setWatchedYear(id, 2027)
        repository.setWatchedYear(id, 2019)

        assertEquals(original, watchedAtOf(id))
        assertTrue(firestoreDataSource.upsertedMedia.isEmpty())
    }

    @Test
    fun `setWatchedYear sur l'annee deja enregistree n'ecrit rien`() = runTest {
        val id = addVu(watchedAt = millis(2024, 3, 9))

        repository.setWatchedYear(id, 2024)

        assertEquals(millis(2024, 3, 9), watchedAtOf(id))
        assertTrue(firestoreDataSource.upsertedMedia.isEmpty())
    }

    @Test
    fun `l'annee choisie survit a une edition de la note meme depuis une copie obsolete`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))
        val staleCopy = repository.observeMediaById(id).first()!!
        repository.setWatchedYear(id, 2024)

        repository.updateMedia(staleCopy.copy(rating = 4))

        val media = repository.observeMediaById(id).first()!!
        assertEquals(4, media.rating)
        assertEquals(millis(2024, 7, 1), media.watchedAt)
    }

    @Test
    fun `l'annee choisie est effacee a la sortie de Vu et repasser en Vu remet l'instant present`() = runTest {
        val id = addVu(watchedAt = null)
        repository.setWatchedYear(id, 2024)
        val vu = repository.observeMediaById(id).first()!!

        repository.updateMedia(vu.copy(status = WatchStatus.EN_COURS))
        assertNull(watchedAtOf(id))

        val before = System.currentTimeMillis()
        repository.updateMedia(vu.copy(status = WatchStatus.VU))
        assertTrue(watchedAtOf(id)!! >= before)
    }

    @Test
    fun `setWatchedYear repercute la nouvelle date vers Firestore`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))

        repository.setWatchedYear(id, 2024)

        val pushed = firestoreDataSource.upsertedMedia.single()
        assertEquals(id, pushed.id)
        assertEquals(millis(2024, 7, 1), pushed.watchedAt)
    }

    @Test
    fun `un echec Firestore n'empeche pas setWatchedYear d'ecrire en local`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))
        firestoreDataSource.shouldThrowOnWrite = true

        val result = repository.setWatchedYear(id, 2024)

        assertTrue(result is Resource.Success)
        assertEquals(millis(2024, 7, 1), watchedAtOf(id))
    }

    @Test
    fun `setWatchedYear renvoie une erreur structuree si le dao echoue`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))
        dao.shouldThrowOnUpdate = true

        assertTrue(repository.setWatchedYear(id, 2024) is Resource.Error)
    }

    @Test
    fun `deleteMedia retire le contenu du suivi`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data

        repository.deleteMedia(addedId)

        assertTrue(repository.observeMedia().first().isEmpty())
    }

    @Test
    fun `addMedia renvoie une erreur structuree si le dao echoue`() = runTest {
        dao.shouldThrowOnInsert = true

        val result = repository.addMedia(dune)

        assertTrue(result is Resource.Error)
    }

    @Test
    fun `addMedia repercute le contenu vers Firestore`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data

        assertEquals(listOf(addedId), firestoreDataSource.upsertedMedia.map { it.id })
    }

    @Test
    fun `un echec Firestore n'empeche pas l'ecriture locale`() = runTest {
        firestoreDataSource.shouldThrowOnWrite = true

        val result = repository.addMedia(dune)

        assertTrue(result is Resource.Success)
        assertEquals(1, repository.observeMedia().first().size)
    }

    @Test
    fun `deconnexion vide le suivi local`() = runTest {
        repository.addMedia(dune)
        assertEquals(1, repository.observeMedia().first().size)

        authRepository.signOut()

        assertTrue(repository.observeMedia().first().isEmpty())
    }

    @Test
    fun `deconnexion purge aussi le cache disque de Firestore`() = runTest {
        repository.addMedia(dune)

        authRepository.signOut()

        assertEquals(1, firestoreDataSource.clearLocalCacheCallCount)
    }

    @Test
    fun `la synchro reprend apres une erreur Firestore`() = runTest {
        // Même horloge virtuelle que le test, sinon les délais entre deux tentatives
        // n'avanceraient jamais.
        authRepository = FakeAuthRepository(user = null)
        firestoreDataSource = FakeFirestoreMediaDataSource().apply { failedObserveAttempts = 2 }
        buildRepository(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        firestoreDataSource.remoteMedia.value = listOf(dune.copy(id = "distant-1"))

        authRepository.signInAs(AuthUser(uid = "user", displayName = "Spectateur"))
        advanceUntilIdle()

        // Sans retry, les deux premières erreurs auraient coupé la synchro définitivement et le
        // contenu distant ne serait jamais arrivé jusqu'à Room.
        assertEquals(3, firestoreDataSource.observeCallCount)
        assertEquals(listOf("distant-1"), repository.observeMedia().first().map { it.id })
    }

    @Test
    fun `mirrorIntoRoom reflete un ajout distant`() = runTest {
        val distant = dune.copy(id = "distant-1")

        firestoreDataSource.remoteMedia.value = listOf(distant)

        val media = repository.observeMedia().first()
        assertEquals(listOf("distant-1"), media.map { it.id })
    }

    @Test
    fun `mirrorIntoRoom supprime localement un contenu retire de Firestore`() = runTest {
        val distant = dune.copy(id = "distant-1")
        firestoreDataSource.remoteMedia.value = listOf(distant)
        assertEquals(1, repository.observeMedia().first().size)

        firestoreDataSource.remoteMedia.value = emptyList()

        assertTrue(repository.observeMedia().first().isEmpty())
    }

    @Test
    fun `bootstrap uploade le suivi local si Firestore est vide a la connexion`() = runTest {
        // Suivi local présent avant toute connexion (déconnecté au départ).
        authRepository = FakeAuthRepository(user = null)
        firestoreDataSource = FakeFirestoreMediaDataSource()
        buildRepository()
        dao.insert(dune.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Spectateur"))

        assertEquals(1, firestoreDataSource.uploadAllCallCount)
        assertEquals(listOf("local-1"), firestoreDataSource.remoteMedia.value.map { it.id })
    }

    @Test
    fun `bootstrap n'ecrase pas Firestore si du contenu y est deja present`() = runTest {
        val distant = dune.copy(id = "distant-1")
        authRepository = FakeAuthRepository(user = null)
        firestoreDataSource = FakeFirestoreMediaDataSource().apply { remoteMedia.value = listOf(distant) }
        buildRepository()
        dao.insert(dune.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "existing-user", displayName = "Spectateur"))

        assertEquals(0, firestoreDataSource.uploadAllCallCount)
        // Le miroir Firestore -> Room fait ensuite autorité : le contenu distant remplace le local.
        val media = repository.observeMedia().first()
        assertEquals(listOf("distant-1"), media.map { it.id })
    }
}
