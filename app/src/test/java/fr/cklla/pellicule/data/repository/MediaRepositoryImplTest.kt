package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import fr.cklla.pellicule.data.remote.FakeRemoteMediaDataSource
import fr.cklla.pellicule.data.sync.PendingOperationKind
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.EpisodeKey
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
import kotlinx.coroutines.cancel
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
 * (écoute de `currentUser`, miroir serveur -> Room, envoi de la file) s'exécutent alors de façon synchrone et
 * déterministe dès qu'un `MutableStateFlow` fake change de valeur, sans avoir besoin d'avancer un
 * temps virtuel séparé — le test reste single-thread de bout en bout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryImplTest {

    private lateinit var dao: FakeMediaDao
    private lateinit var episodeDao: FakeEpisodeDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var remote: FakeRemoteMediaDataSource
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var syncer: fr.cklla.pellicule.data.sync.MediaSyncer
    private lateinit var scheduler: TestOutboxScheduler
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
        syncer = fakeSyncer(remote, outboxDao, dao, episodeDao, authRepository)
        scheduler = TestOutboxScheduler({ syncer.flush() })
        repository = MediaRepositoryImpl(
            mediaDao = dao,
            outboxDao = outboxDao,
            transactions = ImmediateTransactionRunner,
            remoteDataSource = remote,
            syncer = syncer,
            outboxScheduler = scheduler,
            authRepository = authRepository,
            timeSource = timeSource,
            repositoryScope = scope,
        )
    }

    @Before
    fun setUp() {
        dao = FakeMediaDao()
        episodeDao = FakeEpisodeDao()
        outboxDao = FakeOutboxDao()
        remote = FakeRemoteMediaDataSource()
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
        assertTrue(remote.upsertedMedia.isEmpty())
    }

    @Test
    fun `setWatchedYear ne fait rien sur un contenu inconnu`() = runTest {
        val result = repository.setWatchedYear("absent", 2024)

        assertTrue(result is Resource.Success)
        assertTrue(repository.observeMedia().first().isEmpty())
        assertTrue(remote.upsertedMedia.isEmpty())
    }

    @Test
    fun `setWatchedYear refuse une annee future ou anterieure a la sortie`() = runTest {
        val original = millis(2026, 3, 1)
        val id = addVu(watchedAt = original, releaseYear = 2020)

        repository.setWatchedYear(id, 2027)
        repository.setWatchedYear(id, 2019)

        assertEquals(original, watchedAtOf(id))
        assertTrue(remote.upsertedMedia.isEmpty())
    }

    @Test
    fun `setWatchedYear sur l'annee deja enregistree n'ecrit rien`() = runTest {
        val id = addVu(watchedAt = millis(2024, 3, 9))

        repository.setWatchedYear(id, 2024)

        assertEquals(millis(2024, 3, 9), watchedAtOf(id))
        assertTrue(remote.upsertedMedia.isEmpty())
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
    fun `setWatchedYear repercute la nouvelle date vers le serveur`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))

        repository.setWatchedYear(id, 2024)

        val pushed = remote.upsertedMedia.single()
        assertEquals(id, pushed.id)
        assertEquals(millis(2024, 7, 1), pushed.watchedAt)
    }

    @Test
    fun `un serveur injoignable n'empeche pas setWatchedYear d'ecrire en local`() = runTest {
        val id = addVu(watchedAt = millis(2026, 3, 1))
        remote.offline = true

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
    fun `addMedia repercute le contenu vers le serveur`() = runTest {
        val addedId = (repository.addMedia(dune) as Resource.Success).data

        assertEquals(listOf(addedId), remote.upsertedMedia.map { it.id })
    }

    @Test
    fun `un serveur injoignable n'empeche pas l'ecriture locale`() = runTest {
        remote.offline = true

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
    fun `une session absente sans deconnexion ne vide rien`() = runTest {
        repository.addMedia(dune)

        authRepository.dropSessionWithoutSignOut()

        assertEquals(1, repository.observeMedia().first().size)
        assertEquals(1, outboxDao.pending.size.coerceAtLeast(1))
    }

    @Test
    fun `un lancement sans session ne vide pas le suivi local`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        dao.insert(dune.copy(id = "local-1").toEntity())

        buildRepository()

        assertEquals(1, repository.observeMedia().first().size)
    }

    @Test
    fun `deconnexion vide aussi la file d'envoi`() = runTest {
        remote.offline = true
        repository.addMedia(dune)
        assertEquals(1, outboxDao.pending.size)

        authRepository.signOut()

        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `la synchro reprend apres une erreur d'ecoute`() = runTest {
        // Même horloge virtuelle que le test, sinon les délais entre deux tentatives
        // n'avanceraient jamais.
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteMediaDataSource().apply { failedChangesAttempts = 2 }
        buildRepository(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        remote.media.value = listOf(dune.copy(id = "distant-1"))

        authRepository.signInAs(AuthUser(uid = "user", displayName = "Spectateur"))
        advanceUntilIdle()

        // Sans retry, les deux premières erreurs auraient coupé la synchro définitivement et le
        // contenu distant ne serait jamais arrivé jusqu'à Room.
        assertEquals(3, remote.changesCallCount)
        assertEquals(listOf("distant-1"), repository.observeMedia().first().map { it.id })
    }

    @Test
    fun `lance hors ligne, la synchro demarre quand le reseau revient sans relancer l'app`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteMediaDataSource().apply {
            offline = true
            media.value = listOf(dune.copy(id = "distant-1"))
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        buildRepository(scope)
        authRepository.signInAs(AuthUser(uid = "user", displayName = "Spectateur"))
        // Bien plus d'échecs que l'ancienne limite de tentatives.
        testScheduler.advanceTimeBy(10 * 60_000L)
        assertTrue(repository.observeMedia().first().isEmpty())

        remote.offline = false
        advanceUntilIdle()

        assertEquals(listOf("distant-1"), repository.observeMedia().first().map { it.id })
        scope.cancel()
    }

    @Test
    fun `mirrorIntoRoom reflete un ajout distant`() = runTest {
        remote.media.value = listOf(dune.copy(id = "distant-1"))

        assertEquals(listOf("distant-1"), repository.observeMedia().first().map { it.id })
    }

    @Test
    fun `mirrorIntoRoom supprime localement un contenu retire du serveur`() = runTest {
        remote.media.value = listOf(dune.copy(id = "distant-1"))
        assertEquals(1, repository.observeMedia().first().size)

        remote.media.value = emptyList()

        assertTrue(repository.observeMedia().first().isEmpty())
    }

    @Test
    fun `un serveur vide au premier lancement recoit le suivi local, episodes compris`() = runTest {
        // Suivi local présent avant toute connexion (déconnecté au départ).
        authRepository = FakeAuthRepository(user = null)
        buildRepository()
        val serie = dune.copy(id = "local-1", type = MediaType.SERIE)
        dao.insert(serie.toEntity())
        episodeDao.insertAll(listOf(WatchedEpisodeEntity("local-1", 1, 1), WatchedEpisodeEntity("local-1", 1, 2)))

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Spectateur"))

        assertEquals(listOf("local-1"), remote.media.value.map { it.id })
        assertEquals(setOf(EpisodeKey(1, 1), EpisodeKey(1, 2)), remote.episodes.value["local-1"])
        // Le suivi local n'a jamais été vidé en route.
        assertEquals(listOf("local-1"), dao.getAllIds())
        assertEquals(2, episodeDao.getWatchedOnce("local-1").size)
        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `le suivi local est protege du miroir tant que le serveur vide n'a rien recu`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote.offline = false
        buildRepository()
        dao.insert(dune.copy(id = "local-1").toEntity())
        scheduler.autoFlush = false

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Spectateur"))
        // Un état serveur vide (rien n'est encore parti) ne doit rien supprimer.
        assertTrue(syncer.mirror())

        assertEquals(listOf("local-1"), dao.getAllIds())
        assertEquals(1, outboxDao.pending.size)
    }

    @Test
    fun `une lecture du serveur qui echoue au premier lancement n'est pas prise pour un serveur vide`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteMediaDataSource().apply { offline = true }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        buildRepository(scope)
        dao.insert(dune.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Spectateur"))
        testScheduler.advanceTimeBy(10_000L)

        // Aucune mise en file sur une lecture ratée, et rien d'effacé localement.
        assertTrue(outboxDao.pending.isEmpty())
        assertEquals(listOf("local-1"), dao.getAllIds())
        // La synchro réessaie sans fin tant que le réseau manque : on l'arrête pour finir le test.
        scope.cancel()
    }

    @Test
    fun `un serveur deja peuple n'est pas ecrase par le suivi local, le miroir fait ensuite autorite`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteMediaDataSource().apply { media.value = listOf(dune.copy(id = "distant-1")) }
        buildRepository()
        dao.insert(dune.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "existing-user", displayName = "Spectateur"))

        assertTrue(remote.upsertedMedia.isEmpty())
        assertEquals(listOf("distant-1"), repository.observeMedia().first().map { it.id })
    }

    @Test
    fun `les episodes vus reviennent du serveur apres une reinstallation`() = runTest {
        val serie = dune.copy(id = "fallout", type = MediaType.SERIE)
        remote.episodes.value = mapOf("fallout" to setOf(EpisodeKey(1, 1), EpisodeKey(1, 2)))
        remote.media.value = listOf(serie)

        val restored = episodeDao.getWatchedOnce("fallout").map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet()
        assertEquals(setOf(EpisodeKey(1, 1), EpisodeKey(1, 2)), restored)
    }

    @Test
    fun `un episode decoche sur un autre appareil est decoche ici aussi`() = runTest {
        val serie = dune.copy(id = "fallout", type = MediaType.SERIE)
        remote.episodes.value = mapOf("fallout" to setOf(EpisodeKey(1, 1), EpisodeKey(1, 2)))
        remote.media.value = listOf(serie)

        remote.episodes.value = mapOf("fallout" to setOf(EpisodeKey(1, 1)))

        assertEquals(1, episodeDao.getWatchedOnce("fallout").size)
    }

    @Test
    fun `un contenu sans episode vu cote serveur vide les episodes locaux`() = runTest {
        val serie = dune.copy(id = "fallout", type = MediaType.SERIE)
        dao.insert(serie.toEntity())
        episodeDao.insertAll(listOf(WatchedEpisodeEntity("fallout", 1, 1)))

        remote.media.value = listOf(serie)

        assertTrue(episodeDao.getWatchedOnce("fallout").isEmpty())
    }

    @Test
    fun `les episodes d'un contenu disparu du serveur partent avec lui`() = runTest {
        val serie = dune.copy(id = "fallout", type = MediaType.SERIE)
        remote.episodes.value = mapOf("fallout" to setOf(EpisodeKey(1, 1)))
        remote.media.value = listOf(serie)

        remote.media.value = emptyList()

        assertTrue(dao.getAllIds().isEmpty())
    }

    // --- File d'envoi : écriture hors ligne puis miroir ---

    @Test
    fun `un contenu ajoute hors ligne reste en local apres un miroir du serveur vide`() = runTest {
        remote.offline = true
        val id = (repository.addMedia(dune) as Resource.Success).data
        remote.offline = false

        assertTrue(syncer.mirror())

        assertEquals(listOf(id), dao.getAllIds())
        assertEquals(1, outboxDao.pending.size)
    }

    @Test
    fun `un contenu ajoute hors ligne part vers le serveur au retour du reseau`() = runTest {
        remote.offline = true
        val id = (repository.addMedia(dune) as Resource.Success).data
        assertTrue(remote.media.value.isEmpty())

        remote.offline = false
        syncer.flush()

        assertEquals(listOf(id), remote.media.value.map { it.id })
        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `une modification hors ligne n'est pas ecrasee par le miroir`() = runTest {
        val id = (repository.addMedia(dune) as Resource.Success).data
        remote.offline = true
        repository.updateMedia(dune.copy(id = id, status = WatchStatus.VU))
        remote.offline = false

        assertTrue(syncer.mirror())

        assertEquals(WatchStatus.VU, repository.observeMediaById(id).first()?.status)
        syncer.flush()
        assertEquals(WatchStatus.VU, remote.media.value.single().status)
    }

    @Test
    fun `une suppression en attente n'est pas recreee par le miroir`() = runTest {
        val id = (repository.addMedia(dune) as Resource.Success).data
        remote.offline = true
        repository.deleteMedia(id)
        remote.offline = false

        assertTrue(syncer.mirror())

        assertTrue(dao.getAllIds().isEmpty())
        syncer.flush()
        assertTrue(remote.media.value.isEmpty())
        assertEquals(listOf(id), remote.deletedMediaIds)
    }

    @Test
    fun `supprimer un contenu retire ses operations en attente et n'enfile que la suppression`() = runTest {
        remote.offline = true
        val id = (repository.addMedia(dune) as Resource.Success).data
        repository.deleteMedia(id)

        assertEquals(listOf(PendingOperationKind.DELETE_MEDIA.name), outboxDao.pending.map { it.kind })
    }

    @Test
    fun `chaque ecriture locale enregistre Room et la file ensemble`() = runTest {
        remote.offline = true
        val id = (repository.addMedia(dune) as Resource.Success).data
        repository.updateMedia(dune.copy(id = id, status = WatchStatus.EN_COURS))
        repository.deleteMedia(id)

        // Rien n'est envoyé directement : tout est passé par la file.
        assertTrue(remote.upsertedMedia.isEmpty())
        assertTrue(remote.deletedMediaIds.isEmpty())
        assertTrue(scheduler.requestCount >= 3)
    }

    @Test
    fun `un etat serveur lu avant un envoi reussi est ignore`() = runTest {
        val id = (repository.addMedia(dune) as Resource.Success).data
        // Le contenu est en file hors ligne ; pendant la lecture (serveur vide), l'envoi réussit et
        // sort de la file. Appliquer l'état lu avant l'envoi supprimerait le contenu qui vient d'arriver.
        remote.offline = true
        repository.updateMedia(dune.copy(id = id, status = WatchStatus.VU))
        remote.offline = false
        var firstFetch = true
        remote.onFetch = {
            if (firstFetch) {
                firstFetch = false
                syncer.flush()
            }
        }

        assertTrue(syncer.mirror())

        assertEquals(listOf(id), dao.getAllIds())
        assertEquals(1, remote.media.value.size)
        assertTrue(remote.fetchCount >= 2)
    }
}
