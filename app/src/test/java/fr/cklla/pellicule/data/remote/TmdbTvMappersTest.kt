package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonSummaryDto
import fr.cklla.pellicule.data.remote.dto.TmdbTvDetailsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTvMappersTest {

    @Test
    fun `la liste des saisons est convertie dans l'ordre`() {
        val dto = TmdbTvDetailsDto(
            seasons = listOf(
                TmdbSeasonSummaryDto(seasonNumber = 0, name = "Spéciaux", episodeCount = 2),
                TmdbSeasonSummaryDto(seasonNumber = 1, name = "Saison 1", episodeCount = 8, posterPath = "/poster.jpg"),
            ),
        )

        val result = dto.toDomain()

        assertEquals(2, result.size)
        assertEquals(0, result[0].seasonNumber)
        assertEquals(1, result[1].seasonNumber)
        assertEquals("${TmdbApi.POSTER_BASE_URL}/poster.jpg", result[1].posterUrl)
    }

    @Test
    fun `un episode sans image renvoie une still nulle`() {
        val dto = TmdbEpisodeDto(seasonNumber = 1, episodeNumber = 1, name = "Pilote", stillPath = null)

        val result = dto.toDomain()

        assertEquals("Pilote", result.title)
        assertNull(result.stillUrl)
    }

    @Test
    fun `le chemin d'image d'episode est prefixe par l'url de base des vignettes`() {
        val dto = TmdbEpisodeDto(seasonNumber = 1, episodeNumber = 2, name = "Suite", stillPath = "/still.jpg")

        val result = dto.toDomain()

        assertEquals("${TmdbApi.STILL_BASE_URL}/still.jpg", result.stillUrl)
    }

    @Test
    fun `la liste des episodes d'une saison est convertie dans l'ordre`() {
        val dto = TmdbSeasonDto(
            episodes = listOf(
                TmdbEpisodeDto(seasonNumber = 1, episodeNumber = 1, name = "Un"),
                TmdbEpisodeDto(seasonNumber = 1, episodeNumber = 2, name = "Deux"),
            ),
        )

        val result = dto.toDomain()

        assertEquals(listOf("Un", "Deux"), result.map { it.title })
    }
}
