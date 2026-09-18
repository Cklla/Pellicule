package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynopsisRepositoryImplTest {

    @Test
    fun `getSynopsis renvoie le synopsis francais d'un film`() = runTest {
        val api = FakeTmdbApi().apply { overviewByLanguage = mapOf("fr-FR" to "Un synopsis en français.") }
        val repository = SynopsisRepositoryImpl(api)

        val result = repository.getSynopsis(1, MediaType.FILM) as Resource.Success

        assertEquals("Un synopsis en français.", result.data)
    }

    @Test
    fun `getSynopsis renvoie le synopsis francais d'une serie ou d'un anime`() = runTest {
        val api = FakeTmdbApi().apply { overviewByLanguage = mapOf("fr-FR" to "Un synopsis en français.") }
        val repository = SynopsisRepositoryImpl(api)

        val result = repository.getSynopsis(1, MediaType.SERIE) as Resource.Success

        assertEquals("Un synopsis en français.", result.data)
    }

    @Test
    fun `getSynopsis se replie en anglais si le francais est vide`() = runTest {
        val api = FakeTmdbApi().apply {
            overviewByLanguage = mapOf("fr-FR" to "", "en-US" to "An English overview.")
        }
        val repository = SynopsisRepositoryImpl(api)

        val result = repository.getSynopsis(1, MediaType.ANIME) as Resource.Success

        assertEquals("An English overview.", result.data)
    }

    @Test
    fun `un echec reseau renvoie une erreur structuree`() = runTest {
        val api = FakeTmdbApi().apply { shouldThrow = true }
        val repository = SynopsisRepositoryImpl(api)

        assertTrue(repository.getSynopsis(1, MediaType.FILM) is Resource.Error)
    }
}
