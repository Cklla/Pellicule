package fr.cklla.pellicule.ui.stats

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Millisecondes d'un jour de mi-année dans le fuseau de l'appareil, loin de tout changement d'année. */
fun watchedOn(year: Int): Long = ZonedDateTime.of(year, 6, 15, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

class StatsCalculationsTest {

    private fun media(id: String, type: MediaType, status: WatchStatus = WatchStatus.VU, year: Int? = null) =
        Media(id = id, title = id, type = type, status = status, watchedAt = year?.let(::watchedOn))

    private val library = listOf(
        media("film-2026", MediaType.FILM, year = 2026),
        media("serie-2026-a", MediaType.SERIE, year = 2026),
        media("serie-2026-b", MediaType.SERIE, year = 2026),
        media("anime-2025", MediaType.ANIME, year = 2025),
        media("film-sans-annee", MediaType.FILM),
        media("a-voir", MediaType.FILM, WatchStatus.A_VOIR),
        media("en-cours", MediaType.SERIE, WatchStatus.EN_COURS),
    )

    @Test
    fun `toutes annees et tous types compte chaque contenu vu, y compris sans annee`() {
        val stats = computeStats(library)

        assertEquals(5, stats.watchedCount)
        assertEquals(mapOf(MediaType.FILM to 2, MediaType.SERIE to 2, MediaType.ANIME to 1), stats.countsByType)
    }

    @Test
    fun `seuls les contenus vus comptent`() {
        val stats = computeStats(listOf(media("a-voir", MediaType.FILM, WatchStatus.A_VOIR), media("en-cours", MediaType.SERIE, WatchStatus.EN_COURS)))

        assertEquals(0, stats.watchedCount)
        assertEquals(MediaType.entries.associateWith { 0 }, stats.countsByType)
    }

    @Test
    fun `une annee ne garde que les visionnages de cette annee`() {
        val stats = computeStats(library, selectedYear = 2026)

        assertEquals(3, stats.watchedCount)
        assertEquals(mapOf(MediaType.FILM to 1, MediaType.SERIE to 2, MediaType.ANIME to 0), stats.countsByType)
    }

    @Test
    fun `un contenu vu sans annee ne compte que sans annee selectionnee`() {
        assertEquals(2, computeStats(library).countsByType.getValue(MediaType.FILM))
        assertEquals(1, computeStats(library, selectedYear = 2026).countsByType.getValue(MediaType.FILM))
        assertEquals(0, computeStats(library, selectedYear = 2025).countsByType.getValue(MediaType.FILM))
    }

    @Test
    fun `le type reduit le total mais pas la repartition`() {
        val stats = computeStats(library, selectedYear = 2026, selectedType = MediaType.SERIE)

        assertEquals(2, stats.watchedCount)
        assertEquals(mapOf(MediaType.FILM to 1, MediaType.SERIE to 2, MediaType.ANIME to 0), stats.countsByType)
    }

    @Test
    fun `annee sans visionnage donne zero partout`() {
        val stats = computeStats(library, selectedYear = 2020)

        assertEquals(0, stats.watchedCount)
        assertEquals(MediaType.entries.associateWith { 0 }, stats.countsByType)
    }

    @Test
    fun `le total coincide avec la liste de la Bibliotheque filtree de la meme facon`() {
        val stats = computeStats(library, selectedYear = 2026, selectedType = MediaType.FILM)

        assertEquals(
            fr.cklla.pellicule.ui.bibliotheque.filterMedia(
                library,
                fr.cklla.pellicule.ui.bibliotheque.BibliothequeFilter.VU,
                2026,
                MediaType.FILM,
            ).size,
            stats.watchedCount,
        )
    }

    @Test
    fun `donutSegments est vide sans rien a repartir`() {
        assertTrue(donutSegments(MediaType.entries.associateWith { 0 }).isEmpty())
    }

    @Test
    fun `donutSegments part du haut, suit l'ordre des types et couvre 360 degres`() {
        val segments = donutSegments(mapOf(MediaType.FILM to 1, MediaType.SERIE to 2, MediaType.ANIME to 1))

        assertEquals(listOf(MediaType.FILM, MediaType.SERIE, MediaType.ANIME), segments.map { it.type })
        assertEquals(-90f, segments.first().startAngle, 0.001f)
        assertEquals(90f, segments[0].sweepAngle, 0.001f)
        assertEquals(180f, segments[1].sweepAngle, 0.001f)
        assertEquals(0f, segments[1].startAngle, 0.001f)
        assertEquals(360f, segments.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `donutSegments omet les types a zero`() {
        val segments = donutSegments(mapOf(MediaType.FILM to 0, MediaType.SERIE to 3, MediaType.ANIME to 0))

        assertEquals(listOf(MediaType.SERIE), segments.map { it.type })
        assertEquals(360f, segments.single().sweepAngle, 0.001f)
    }
}
