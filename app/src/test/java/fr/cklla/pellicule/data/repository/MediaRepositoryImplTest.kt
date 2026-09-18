package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.firestore.FakeFirestoreMediaDataSource
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
