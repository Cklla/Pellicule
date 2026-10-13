package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.FakeRemoteMediaDataSource
import fr.cklla.pellicule.data.sync.PendingOperationKind
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeRepositoryImplTest {

    @Test
    fun `marquer un episode vu le fait apparaitre dans les episodes vus`() = runTest {
        val repository = fakeEpisodeRepository()

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertEquals(setOf(EpisodeKey(1, 1)), repository.observeWatchedEpisodes("media-1").first())
    }

    @Test
    fun `marquer un episode non vu le retire des episodes vus`() = runTest {
        val repository = fakeEpisodeRepository()
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = false)

        assertTrue(repository.observeWatchedEpisodes("media-1").first().isEmpty())
    }

    @Test
    fun `le statut d'un episode est isole par contenu suivi`() = runTest {
        val repository = fakeEpisodeRepository()

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertTrue(repository.observeWatchedEpisodes("media-2").first().isEmpty())
    }

    @Test
    fun `replaceWatchedEpisodes remplace entierement le set d'un contenu`() = runTest {
        val repository = fakeEpisodeRepository()
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 2), watched = true)

        repository.replaceWatchedEpisodes("media-1", setOf(EpisodeKey(1, 2), EpisodeKey(1, 3)))

        assertEquals(setOf(EpisodeKey(1, 2), EpisodeKey(1, 3)), repository.observeWatchedEpisodes("media-1").first())
    }

    @Test
    fun `replaceWatchedEpisodes ne touche pas les autres contenus`() = runTest {
        val repository = fakeEpisodeRepository()
        repository.setEpisodeWatched("media-2", EpisodeKey(1, 1), watched = true)

        repository.replaceWatchedEpisodes("media-1", setOf(EpisodeKey(1, 5)))

        assertEquals(setOf(EpisodeKey(1, 1)), repository.observeWatchedEpisodes("media-2").first())
    }

    private val serie = Media(id = "media-1", title = "Fallout", type = MediaType.SERIE, status = WatchStatus.EN_COURS)

    private fun serverWithSerie() = FakeRemoteMediaDataSource().apply { media.value = listOf(serie) }

    @Test
    fun `cocher un episode l'envoie au serveur`() = runTest {
        val server = serverWithSerie()
        val repository = fakeEpisodeRepository(remote = server)

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 3), watched = true)

        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 3))), server.addedEpisodes)
        assertTrue(server.removedEpisodes.isEmpty())
    }

    @Test
    fun `decocher un episode le retire du serveur`() = runTest {
        val server = serverWithSerie()
        val repository = fakeEpisodeRepository(remote = server)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 3), watched = true)

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 3), watched = false)

        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 3))), server.removedEpisodes)
        assertTrue(server.episodes.value["media-1"].orEmpty().isEmpty())
    }

    @Test
    fun `replaceWatchedEpisodes ne met en file que la difference`() = runTest {
        val server = serverWithSerie()
        val repository = fakeEpisodeRepository(remote = server)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 2), watched = true)
        server.addedEpisodes.clear()

        repository.replaceWatchedEpisodes("media-1", setOf(EpisodeKey(1, 2), EpisodeKey(1, 3)))

        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 3))), server.addedEpisodes)
        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 1))), server.removedEpisodes)
    }

    @Test
    fun `replaceWatchedEpisodes sans changement n'envoie rien`() = runTest {
        val server = serverWithSerie()
        val outbox = FakeOutboxDao()
        val repository = fakeEpisodeRepository(remote = server, outboxDao = outbox)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)
        server.addedEpisodes.clear()

        repository.replaceWatchedEpisodes("media-1", setOf(EpisodeKey(1, 1)))

        assertTrue(server.addedEpisodes.isEmpty())
        assertTrue(server.removedEpisodes.isEmpty())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `un serveur injoignable ne fait pas echouer l'ecriture locale et garde l'episode en file`() = runTest {
        val server = serverWithSerie().apply { offline = true }
        val outbox = FakeOutboxDao()
        val repository = fakeEpisodeRepository(remote = server, outboxDao = outbox)

        val result = repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertTrue(result is Resource.Success)
        assertEquals(setOf(EpisodeKey(1, 1)), repository.observeWatchedEpisodes("media-1").first())
        assertEquals(listOf(PendingOperationKind.ADD_EPISODES.name), outbox.pending.map { it.kind })
    }

    @Test
    fun `plusieurs episodes coches hors ligne partent ensemble au retour du reseau`() = runTest {
        val server = serverWithSerie().apply { offline = true }
        val outbox = FakeOutboxDao()
        val syncer = fakeSyncer(server, outbox)
        val repository = fakeEpisodeRepository(remote = server, outboxDao = outbox, syncer = syncer)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 2), watched = true)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = false)

        server.offline = false
        syncer.flush()

        // Rejoués dans l'ordre : l'épisode coché puis décoché n'est pas vu au final.
        assertEquals(setOf(EpisodeKey(1, 2)), server.episodes.value["media-1"])
        assertTrue(outbox.pending.isEmpty())
    }
}
