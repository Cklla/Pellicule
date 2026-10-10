package fr.cklla.pellicule.data.remote.supabase

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRowsTest {

    // Même configuration que le client par défaut du SDK.
    private val json = Json { ignoreUnknownKeys = true }

    private val media = Media(
        id = "5d1a7a3e-0000-4000-8000-000000000001",
        title = "Arcane",
        type = MediaType.ANIME,
        status = WatchStatus.VU,
        tmdbId = 94605,
        releaseYear = 2021,
        posterUrl = "https://image.tmdb.org/t/p/w500/arcane.jpg",
        jellyfinId = "jf-1",
        rating = 5,
        watchedAt = 1_700_000_000_000,
    )

    @Test
    fun `un contenu fait l'aller-retour par son JSON`() {
        val row = json.decodeFromString<MediaRow>(json.encodeToString(MediaRow.serializer(), media.toRow()))

        assertEquals(media, row.toMediaOrNull())
    }

    @Test
    fun `les colonnes portent les noms de la base`() {
        val row = json.parseToJsonElement(json.encodeToString(MediaRow.serializer(), media.toRow())).jsonObject

        assertEquals("Arcane", row.getValue("title").jsonPrimitive.content)
        assertEquals("ANIME", row.getValue("type").jsonPrimitive.content)
        assertEquals("94605", row.getValue("tmdb_id").jsonPrimitive.content)
        assertEquals(2021, row.getValue("release_year").jsonPrimitive.int)
        assertEquals("jf-1", row.getValue("jellyfin_id").jsonPrimitive.content)
        assertEquals("1700000000000", row.getValue("watched_at").jsonPrimitive.content)
        assertFalse("user_id est rempli par le serveur", row.containsKey("user_id"))
    }

    @Test
    fun `une valeur absente est envoyee comme null pour effacer la colonne`() {
        val cleared = media.copy(rating = null, watchedAt = null, jellyfinId = null, posterUrl = null)

        val row: JsonObject = json.parseToJsonElement(json.encodeToString(MediaRow.serializer(), cleared.toRow())).jsonObject

        // Sans le null explicite, un upsert laisserait l'ancienne note en base.
        listOf("rating", "watched_at", "jellyfin_id", "poster_url", "tmdb_id").forEach { column ->
            if (column != "tmdb_id") assertEquals("colonne $column", JsonNull, row[column])
        }
    }

    @Test
    fun `une ligne serveur avec des colonnes en plus est lue`() {
        val raw = """{"id":"x","user_id":"u","title":"Dune","type":"FILM","status":"A_VOIR","tmdb_id":null,
            "release_year":null,"poster_url":null,"jellyfin_id":null,"rating":null,"watched_at":null,
            "created_at":"2026-10-10T12:00:00Z"}"""

        val result = json.decodeFromString<MediaRow>(raw).toMediaOrNull()

        assertEquals(Media(id = "x", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR), result)
    }

    @Test
    fun `une ligne au type ou au statut inconnu est ignoree`() {
        val row = media.toRow()

        assertNull(row.copy(type = "DOCUMENTAIRE").toMediaOrNull())
        assertNull(row.copy(status = "ABANDONNE").toMediaOrNull())
    }

    @Test
    fun `une affiche qui n'est pas en https est ecartee`() {
        assertNull(media.toRow().copy(posterUrl = "http://exemple.fr/a.jpg").toMediaOrNull()?.posterUrl)
        assertNull(media.toRow().copy(posterUrl = "file:///etc/passwd").toMediaOrNull()?.posterUrl)
    }

    @Test
    fun `un episode fait l'aller-retour, numeros superieurs a 1000 compris`() {
        val key = EpisodeKey(3, 1243)

        val row = json.decodeFromString<WatchedEpisodeRow>(json.encodeToString(WatchedEpisodeRow.serializer(), key.toRow("m")))

        assertEquals("m", row.mediaId)
        assertEquals(key, row.toEpisodeKey())
        val raw = json.parseToJsonElement(json.encodeToString(WatchedEpisodeRow.serializer(), key.toRow("m"))).jsonObject
        assertEquals(setOf("media_id", "season", "episode"), raw.keys)
    }

    // --- pagination ---

    @Test
    fun `une lecture plus grande qu'une page est lue en entier`() = runTest {
        val source = (1..2500).toList()
        val requested = mutableListOf<Pair<Long, Long>>()

        val all = fetchAllPages(1000) { from, to ->
            requested += from to to
            source.subList(from.toInt(), minOf(to.toInt() + 1, source.size))
        }

        assertEquals(source, all)
        assertEquals(listOf(0L to 999L, 1000L to 1999L, 2000L to 2999L), requested)
    }

    @Test
    fun `une lecture d'exactement une page demande la page suivante pour confirmer la fin`() = runTest {
        val source = (1..1000).toList()
        var calls = 0

        val all = fetchAllPages(1000) { from, to ->
            calls++
            source.subList(minOf(from.toInt(), source.size), minOf(to.toInt() + 1, source.size))
        }

        assertEquals(source, all)
        assertEquals(2, calls)
    }

    @Test
    fun `une lecture vide renvoie une liste vide`() = runTest {
        assertTrue(fetchAllPages(1000) { _, _ -> emptyList<Int>() }.isEmpty())
    }

    @Test
    fun `l'echec d'une page fait echouer toute la lecture au lieu de renvoyer un morceau`() = runTest {
        val failure = runCatching {
            fetchAllPages(2) { from, _ ->
                if (from >= 2) error("coupure") else listOf(1, 2)
            }
        }.exceptionOrNull()

        assertEquals("coupure", failure?.message)
    }

    // --- classification des erreurs ---

    @Test
    fun `les refus de fond sont definitifs, le reste se reessaie`() {
        // CHECK, NOT NULL, clé étrangère, unicité, texte trop long, identifiant mal formé.
        listOf("23514", "23502", "23503", "23505", "22001", "22P02").forEach { assertTrue(it, isPermanentFailure(it)) }
        // Colonne ou table inconnue du schéma déployé, droits (RLS ou GRANT), JWT expiré, code absent.
        listOf("PGRST204", "PGRST205", "42501", "42703", "PGRST301", "", "23").forEach {
            assertFalse(it, isPermanentFailure(it))
        }
        assertFalse(isPermanentFailure(null))
    }
}
