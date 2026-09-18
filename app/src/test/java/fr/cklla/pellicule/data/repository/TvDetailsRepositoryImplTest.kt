package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonSummaryDto
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDetailsRepositoryImplTest {

    @Test
    fun `getSeasons convertit les saisons TMDB`() = runTest {
        val api = FakeTmdbApi().apply {
            seasons = listOf(TmdbSeasonSummaryDto(seasonNumber = 1, name = "Saison 1", episodeCount = 8))
        }
        val repository = TvDetailsRepositoryImpl(api)

        val result = repository.getSeasons(1) as Resource.Success

        assertEquals(1, result.data.size)
        assertEquals("Saison 1", result.data.first().name)
    }

    @Test
    fun `un echec reseau sur getSeasons renvoie une erreur structuree`() = runTest {
        val api = FakeTmdbApi().apply { shouldThrow = true }
        val repository = TvDetailsRepositoryImpl(api)

        assertTrue(repository.getSeasons(1) is Resource.Error)
    }

    @Test
    fun `getEpisodes convertit les episodes TMDB`() = runTest {
        val api = FakeTmdbApi().apply {
            episodes = listOf(TmdbEpisodeDto(seasonNumber = 1, episodeNumber = 1, name = "Pilote"))
        }
        val repository = TvDetailsRepositoryImpl(api)

        val result = repository.getEpisodes(1, 1) as Resource.Success

        assertEquals(1, result.data.size)
        assertEquals("Pilote", result.data.first().title)
    }

    @Test
    fun `un echec reseau sur getEpisodes renvoie une erreur structuree`() = runTest {
        val api = FakeTmdbApi().apply { shouldThrow = true }
        val repository = TvDetailsRepositoryImpl(api)

        assertTrue(repository.getEpisodes(1, 1) is Resource.Error)
    }
}
