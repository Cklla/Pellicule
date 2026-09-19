package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbWatchProviderDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersCountryDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersResponseDto
import fr.cklla.pellicule.domain.model.WatchAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbWatchProvidersMappersTest {

    private fun provider(id: Int, name: String, priority: Int? = null, logoPath: String? = null) =
        TmdbWatchProviderDto(providerId = id, providerName = name, logoPath = logoPath, displayPriority = priority)

    private fun response(vararg countries: Pair<String, TmdbWatchProvidersCountryDto>) =
        TmdbWatchProvidersResponseDto(results = countries.toMap())

    @Test
    fun `une reponse sans aucun pays vaut disponibilite inconnue`() {
        assertTrue(TmdbWatchProvidersResponseDto(results = emptyMap()).toDomain() is WatchAvailability.Unknown)
        assertTrue(TmdbWatchProvidersResponseDto(results = null).toDomain() is WatchAvailability.Unknown)
    }

    @Test
    fun `des offres hors de France valent absence d'offre francaise, pas inconnu`() {
        val response = response(
            "US" to TmdbWatchProvidersCountryDto(flatrate = listOf(provider(99, "Shudder"))),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(emptyList<Any>(), availability.streaming)
        assertEquals(emptyList<Any>(), availability.rentOrBuy)
    }

    @Test
    fun `abonnement, gratuit et publicite sont regroupes en streaming`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(
                flatrate = listOf(provider(8, "Netflix", priority = 0)),
                free = listOf(provider(300, "France TV", priority = 10)),
                ads = listOf(provider(400, "Pluto TV", priority = 20)),
            ),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(listOf("Netflix", "France TV", "Pluto TV"), availability.streaming.map { it.name })
    }

    @Test
    fun `location et achat sont regroupes sur la seconde ligne`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(
                rent = listOf(provider(2, "Apple TV Store", priority = 6)),
                buy = listOf(provider(61, "Orange VOD", priority = 8)),
            ),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(listOf("Apple TV Store", "Orange VOD"), availability.rentOrBuy.map { it.name })
        assertEquals(emptyList<Any>(), availability.streaming)
    }

    @Test
    fun `les variantes d'une meme plateforme sont fusionnees`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(
                flatrate = listOf(
                    provider(1796, "Netflix Standard with Ads", priority = 52),
                    provider(8, "Netflix", priority = 0),
                    provider(1825, "HBO Max Amazon Channel", priority = 104),
                    provider(1899, "HBO Max", priority = 73),
                    provider(2100, "Amazon Prime Video with Ads", priority = 85),
                    provider(119, "Amazon Prime Video", priority = 2),
                ),
            ),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(listOf("Netflix", "Amazon Prime Video", "HBO Max"), availability.streaming.map { it.name })
        assertEquals(listOf(8, 119, 1899), availability.streaming.map { it.id })
    }

    @Test
    fun `une variante seule est affichee sous le nom de sa plateforme`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(
                flatrate = listOf(provider(1825, "HBO Max Amazon Channel", priority = 104)),
            ),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(listOf("HBO Max"), availability.streaming.map { it.name })
    }

    @Test
    fun `deux orthographes d'un meme service ne font qu'une entree`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(
                flatrate = listOf(
                    provider(415, "Animation Digital Network", priority = 12),
                    provider(1949, "Anime Digital Network Amazon Channel", priority = 120),
                ),
            ),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(listOf("Animation Digital Network"), availability.streaming.map { it.name })
    }

    @Test
    fun `le logo est construit a partir du chemin TMDB`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(flatrate = listOf(provider(8, "Netflix", logoPath = "/netflix.jpg"))),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals(TmdbApi.PROVIDER_LOGO_BASE_URL + "/netflix.jpg", availability.streaming.single().logoUrl)
    }

    @Test
    fun `le lien JustWatch du pays est conserve`() {
        val response = response(
            "FR" to TmdbWatchProvidersCountryDto(link = "https://www.themoviedb.org/movie/1/watch?locale=FR"),
        )

        val availability = response.toDomain() as WatchAvailability.Known

        assertEquals("https://www.themoviedb.org/movie/1/watch?locale=FR", availability.link)
    }
}
