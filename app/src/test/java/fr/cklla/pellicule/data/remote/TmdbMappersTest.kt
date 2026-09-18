package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbSearchResultDto
import fr.cklla.pellicule.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMappersTest {

    @Test
    fun `un film sans genre animation japonais devient un FILM`() {
        val dto = TmdbSearchResultDto(
            id = 1,
            mediaType = "movie",
            title = "Dune",
            releaseDate = "2021-09-15",
            genreIds = listOf(878),
            originalLanguage = "en",
        )

        val result = dto.toDomain()

        assertEquals(MediaType.FILM, result?.type)
        assertEquals("Dune", result?.title)
        assertEquals(2021, result?.year)
    }

    @Test
    fun `une serie devient un SERIE`() {
        val dto = TmdbSearchResultDto(id = 2, mediaType = "tv", name = "Severance", firstAirDate = "2022-02-18")

        val result = dto.toDomain()

        assertEquals(MediaType.SERIE, result?.type)
        assertEquals("Severance", result?.title)
    }

    @Test
    fun `un film d'animation japonais devient un ANIME`() {
        val dto = TmdbSearchResultDto(
            id = 3,
            mediaType = "movie",
            title = "Perfect Blue",
            releaseDate = "1997-02-28",
            genreIds = listOf(16, 27),
            originalLanguage = "ja",
        )

        val result = dto.toDomain()

        assertEquals(MediaType.ANIME, result?.type)
    }

    @Test
    fun `une serie d'animation japonaise via origin_country devient un ANIME`() {
        val dto = TmdbSearchResultDto(
            id = 4,
            mediaType = "tv",
            name = "One Piece",
            firstAirDate = "1999-10-20",
            genreIds = listOf(16, 10759),
            originalLanguage = "ja",
            originCountry = listOf("JP"),
        )

        val result = dto.toDomain()

        assertEquals(MediaType.ANIME, result?.type)
    }

    @Test
    fun `un dessin anime non japonais reste un FILM`() {
        val dto = TmdbSearchResultDto(
            id = 5,
            mediaType = "movie",
            title = "Les Triplettes de Belleville",
            releaseDate = "2003-05-06",
            genreIds = listOf(16),
            originalLanguage = "fr",
        )

        val result = dto.toDomain()

        assertEquals(MediaType.FILM, result?.type)
    }

    @Test
    fun `une personne est ignoree`() {
        val dto = TmdbSearchResultDto(id = 6, mediaType = "person", name = "Denis Villeneuve")

        assertNull(dto.toDomain())
    }

    @Test
    fun `un titre absent est ignore`() {
        val dto = TmdbSearchResultDto(id = 7, mediaType = "movie", title = null)

        assertNull(dto.toDomain())
    }

    @Test
    fun `une date de sortie inconnue laisse l'annee a null`() {
        val dto = TmdbSearchResultDto(id = 8, mediaType = "movie", title = "Sans date", releaseDate = null)

        val result = dto.toDomain()

        assertNull(result?.year)
    }

    @Test
    fun `le chemin d'affiche est prefixe par l'url de base TMDB`() {
        val dto = TmdbSearchResultDto(id = 9, mediaType = "movie", title = "Dune", posterPath = "/poster.jpg")

        val result = dto.toDomain()

        assertEquals("${TmdbApi.POSTER_BASE_URL}/poster.jpg", result?.posterUrl)
    }
}
