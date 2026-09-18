package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.dto.TmdbSearchResultDto
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchRepositoryImplTest {

    @Test
    fun `une recherche vide ne fait aucun appel reseau et renvoie une liste vide`() = runTest {
        val api = FakeTmdbApi().apply { shouldThrow = true } // prouve qu'il n'est pas appelé
        val repository = MediaSearchRepositoryImpl(api)

        val result = repository.searchMedia(" ")

        assertEquals(Resource.Success(emptyList<Nothing>()), result)
    }

    @Test
    fun `une recherche reussie convertit les resultats TMDB`() = runTest {
        val api = FakeTmdbApi().apply {
            results = listOf(TmdbSearchResultDto(id = 1, mediaType = "movie", title = "Dune"))
        }
        val repository = MediaSearchRepositoryImpl(api)

        val result = repository.searchMedia("dune") as Resource.Success

        assertEquals(1, result.data.size)
        assertEquals("Dune", result.data.first().title)
    }

    @Test
    fun `les resultats non exploitables sont filtres`() = runTest {
        val api = FakeTmdbApi().apply {
            results = listOf(
                TmdbSearchResultDto(id = 1, mediaType = "person", name = "Denis Villeneuve"),
                TmdbSearchResultDto(id = 2, mediaType = "movie", title = "Dune"),
            )
        }
        val repository = MediaSearchRepositoryImpl(api)

        val result = repository.searchMedia("dune") as Resource.Success

        assertEquals(1, result.data.size)
    }

    @Test
    fun `un echec reseau renvoie une erreur structuree`() = runTest {
        val api = FakeTmdbApi().apply { shouldThrow = true }
        val repository = MediaSearchRepositoryImpl(api)

        val result = repository.searchMedia("dune")

        assertTrue(result is Resource.Error)
    }
}
