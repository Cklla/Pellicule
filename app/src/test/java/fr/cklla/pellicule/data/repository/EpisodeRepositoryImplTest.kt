package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.EpisodeKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeRepositoryImplTest {

    @Test
    fun `marquer un episode vu le fait apparaitre dans les episodes vus`() = runTest {
        val repository = EpisodeRepositoryImpl(FakeEpisodeDao())

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertEquals(setOf(EpisodeKey(1, 1)), repository.observeWatchedEpisodes("media-1").first())
    }

    @Test
    fun `marquer un episode non vu le retire des episodes vus`() = runTest {
        val repository = EpisodeRepositoryImpl(FakeEpisodeDao())
        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = false)

        assertTrue(repository.observeWatchedEpisodes("media-1").first().isEmpty())
    }

    @Test
    fun `le statut d'un episode est isole par contenu suivi`() = runTest {
        val repository = EpisodeRepositoryImpl(FakeEpisodeDao())

        repository.setEpisodeWatched("media-1", EpisodeKey(1, 1), watched = true)

        assertTrue(repository.observeWatchedEpisodes("media-2").first().isEmpty())
    }
}
