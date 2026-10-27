package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieRuntimeRepositoryImplTest {

    @Test
    fun `getRuntimeMinutes renvoie la duree du film`() = runTest {
        val repository = MovieRuntimeRepositoryImpl(FakeTmdbApi().apply { movieRuntime = 136 })

        val result = repository.getRuntimeMinutes(603) as Resource.Success

        assertEquals(136, result.data)
    }

    @Test
    fun `une duree absente renvoie null`() = runTest {
        val repository = MovieRuntimeRepositoryImpl(FakeTmdbApi().apply { movieRuntime = null })

        assertNull((repository.getRuntimeMinutes(1) as Resource.Success).data)
    }

    @Test
    fun `une duree a zero est traitee comme inconnue`() = runTest {
        val repository = MovieRuntimeRepositoryImpl(FakeTmdbApi().apply { movieRuntime = 0 })

        assertNull((repository.getRuntimeMinutes(1) as Resource.Success).data)
    }

    @Test
    fun `un echec reseau renvoie une erreur structuree`() = runTest {
        val repository = MovieRuntimeRepositoryImpl(FakeTmdbApi().apply { shouldThrow = true })

        assertTrue(repository.getRuntimeMinutes(1) is Resource.Error)
    }
}
