package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.dto.TmdbWatchProviderDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersCountryDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersResponseDto
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProvidersRepositoryImplTest {

    private val netflixEnFrance = TmdbWatchProvidersResponseDto(
        results = mapOf(
            "FR" to TmdbWatchProvidersCountryDto(
                link = "https://www.themoviedb.org/movie/1/watch?locale=FR",
                flatrate = listOf(TmdbWatchProviderDto(providerId = 8, providerName = "Netflix", displayPriority = 0)),
            ),
        ),
    )

    @Test
    fun `getAvailability renvoie les plateformes francaises d'un film`() = runTest {
        val api = FakeTmdbApi().apply { watchProviders = netflixEnFrance }

        val result = WatchProvidersRepositoryImpl(api).getAvailability(1, MediaType.FILM) as Resource.Success
        val availability = result.data as WatchAvailability.Known

        assertEquals(listOf("Netflix"), availability.streaming.map { it.name })
    }

    @Test
    fun `getAvailability renvoie les plateformes francaises d'une serie ou d'un anime`() = runTest {
        val api = FakeTmdbApi().apply { watchProviders = netflixEnFrance }

        val result = WatchProvidersRepositoryImpl(api).getAvailability(1, MediaType.SERIE) as Resource.Success
        val availability = result.data as WatchAvailability.Known

        assertEquals(listOf("Netflix"), availability.streaming.map { it.name })
    }

    @Test
    fun `un echec reseau renvoie une erreur structuree`() = runTest {
        val api = FakeTmdbApi().apply { shouldThrow = true }

        assertTrue(WatchProvidersRepositoryImpl(api).getAvailability(1, MediaType.FILM) is Resource.Error)
    }
}
