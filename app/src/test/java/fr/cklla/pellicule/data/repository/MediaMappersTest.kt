package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.entity.MediaEntity
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMappersTest {

    @Test
    fun `domain to entity to domain round trip preserves all fields`() {
        val media = Media(
            id = "uuid-1",
            title = "Perfect Blue",
            type = MediaType.ANIME,
            status = WatchStatus.EN_COURS,
            tmdbId = 10494L,
            releaseYear = 1997,
            posterUrl = "https://example.org/poster.jpg",
            watchedAt = 1_700_000_000_000L,
            notes = "Superbe.\nÀ revoir.",
        )

        val roundTripped = media.toEntity().toDomain()

        assertEquals(media, roundTripped)
    }

    @Test
    fun `entity to domain parses enum names stored as strings`() {
        val entity = MediaEntity(
            id = "uuid-2",
            title = "Severance",
            type = MediaType.SERIE.name,
            status = WatchStatus.A_VOIR.name,
            tmdbId = null,
            releaseYear = null,
            posterUrl = null,
        )

        val media = entity.toDomain()

        assertEquals(MediaType.SERIE, media.type)
        assertEquals(WatchStatus.A_VOIR, media.status)
    }

    @Test
    fun `une entite sans notes donne un contenu sans notes`() {
        val entity = MediaEntity(
            id = "uuid-3",
            title = "Dune",
            type = MediaType.FILM.name,
            status = WatchStatus.A_VOIR.name,
            tmdbId = null,
            releaseYear = null,
            posterUrl = null,
        )

        assertEquals("", entity.toDomain().notes)
        assertEquals("", Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR).toEntity().notes)
    }
}
