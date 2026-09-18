package fr.cklla.pellicule.data.remote.firestore

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreMappersTest {

    @Test
    fun `toFirestoreMap puis mapToMedia conserve toutes les donnees`() {
        val media = Media(
            id = "42",
            title = "Severance",
            type = MediaType.SERIE,
            status = WatchStatus.EN_COURS,
            tmdbId = 95396L,
            releaseYear = 2022,
            posterUrl = "https://example.com/poster.jpg",
            jellyfinId = "jf-42",
            rating = 5,
        )

        val roundTripped = mapToMedia(media.id, media.toFirestoreMap())

        assertEquals(media, roundTripped)
    }

    @Test
    fun `toFirestoreMap n'inclut pas l'id`() {
        val media = Media(id = "42", title = "t", type = MediaType.FILM, status = WatchStatus.A_VOIR)

        assertEquals(false, media.toFirestoreMap().containsKey("id"))
    }

    @Test
    fun `mapToMedia lit des nombres remontes en Long, comme le fait Firestore`() {
        // Firestore n'a pas de type Int natif : tout nombre entier remonte en Long côté SDK réel.
        val data = mapOf(
            "title" to "t",
            "type" to MediaType.FILM.name,
            "status" to WatchStatus.VU.name,
            "tmdbId" to 123L,
            "releaseYear" to 2024L,
            "rating" to 4L,
        )

        val media = mapToMedia("1", data)

        assertEquals(123L, media?.tmdbId)
        assertEquals(2024, media?.releaseYear)
        assertEquals(4, media?.rating)
    }

    @Test
    fun `mapToMedia renvoie null si le type est absent`() {
        val data = mapOf("title" to "t", "status" to WatchStatus.A_VOIR.name)

        assertNull(mapToMedia("1", data))
    }

    @Test
    fun `mapToMedia renvoie null si le statut est inconnu`() {
        val data = mapOf(
            "title" to "t",
            "type" to MediaType.FILM.name,
            "status" to "STATUT_INEXISTANT",
        )

        assertNull(mapToMedia("1", data))
    }

    @Test
    fun `mapToMedia renvoie null si un champ obligatoire est manquant`() {
        val data = mapOf("type" to MediaType.FILM.name, "status" to WatchStatus.A_VOIR.name)

        assertNull(mapToMedia("1", data))
    }

    @Test
    fun `mapToMedia retombe sur des valeurs par defaut pour les champs optionnels absents`() {
        val data = mapOf(
            "title" to "t",
            "type" to MediaType.FILM.name,
            "status" to WatchStatus.A_VOIR.name,
        )

        val media = mapToMedia("1", data)

        assertNull(media?.tmdbId)
        assertNull(media?.releaseYear)
        assertNull(media?.posterUrl)
        assertNull(media?.jellyfinId)
        assertNull(media?.rating)
    }

    @Test
    fun `mapToMedia rejette une posterUrl qui n'est pas en https`() {
        val data = mapOf(
            "title" to "t",
            "type" to MediaType.FILM.name,
            "status" to WatchStatus.A_VOIR.name,
            "posterUrl" to "http://example.com/poster.jpg",
        )

        assertNull(mapToMedia("1", data)?.posterUrl)
    }
}
