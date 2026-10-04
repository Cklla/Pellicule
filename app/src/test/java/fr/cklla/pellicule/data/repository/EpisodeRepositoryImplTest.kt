package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.firestore.FakeFirestoreMediaDataSource
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Resource
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

    @Test
    fun `cocher un episode l'ajoute au document Firestore du contenu`() = runTest {
        val firestore = FakeFirestoreMediaDataSource()
        val repository = fakeEpisodeRepository(firestoreDataSource = firestore)

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 3), watched = true)

        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 3))), firestore.addedEpisodes)
        assertTrue(firestore.removedEpisodes.isEmpty())
    }

    @Test
    fun `decocher un episode le retire du document Firestore`() = runTest {
        val firestore = FakeFirestoreMediaDataSource()
        val repository = fakeEpisodeRepository(firestoreDataSource = firestore)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 3), watched = true)

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 3), watched = false)

        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 3))), firestore.removedEpisodes)
        assertTrue(firestore.remoteEpisodes.value["media-1"].orEmpty().isEmpty())
    }

    @Test
    fun `replaceWatchedEpisodes ne pousse que la difference`() = runTest {
        val firestore = FakeFirestoreMediaDataSource()
        val repository = fakeEpisodeRepository(firestoreDataSource = firestore)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 2), watched = true)
        firestore.addedEpisodes.clear()

        repository.replaceWatchedEpisodes("media-1", setOf(EpisodeKey(1, 2), EpisodeKey(1, 3)))

        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 3))), firestore.addedEpisodes)
        assertEquals(listOf("media-1" to setOf(EpisodeKey(1, 1))), firestore.removedEpisodes)
    }

    @Test
    fun `replaceWatchedEpisodes sans changement n'envoie rien`() = runTest {
        val firestore = FakeFirestoreMediaDataSource()
        val repository = fakeEpisodeRepository(firestoreDataSource = firestore)
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)
        firestore.addedEpisodes.clear()

        repository.replaceWatchedEpisodes("media-1", setOf(EpisodeKey(1, 1)))

        assertTrue(firestore.addedEpisodes.isEmpty())
        assertTrue(firestore.removedEpisodes.isEmpty())
    }

    @Test
    fun `un echec Firestore ne fait pas echouer l'ecriture locale`() = runTest {
        val firestore = FakeFirestoreMediaDataSource().apply { shouldThrowOnWrite = true }
        val repository = fakeEpisodeRepository(firestoreDataSource = firestore)

        val result = repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertTrue(result is Resource.Success)
        assertEquals(setOf(EpisodeKey(1, 1)), repository.observeWatchedEpisodes("media-1").first())
    }

    @Test
    fun `sans utilisateur connecte rien n'est envoye a Firestore`() = runTest {
        val firestore = FakeFirestoreMediaDataSource()
        val repository = fakeEpisodeRepository(firestoreDataSource = firestore, authRepository = FakeAuthRepository(user = null))

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertTrue(firestore.addedEpisodes.isEmpty())
        assertEquals(setOf(EpisodeKey(1, 1)), repository.observeWatchedEpisodes("media-1").first())
    }
}
