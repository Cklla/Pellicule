package fr.cklla.pellicule.domain.recap

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapHighlightsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(month: Int, day: Int, hour: Int = 20, zone: ZoneId = this.zone, year: Int = 2026): Long =
        ZonedDateTime.of(year, month, day, hour, 30, 0, 0, zone).toInstant().toEpochMilli()

    /** Le 1er juillet à midi pile, l'horodatage d'une année choisie à la main. */
    private fun approximate(zone: ZoneId = this.zone, year: Int = 2026): Long =
        ZonedDateTime.of(year, 7, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun media(
        id: String,
        type: MediaType = MediaType.FILM,
        rating: Int? = null,
        watchedAt: Long? = at(6, 15),
        status: WatchStatus = WatchStatus.VU,
        releaseYear: Int? = null,
        posterUrl: String? = null,
    ) = Media(
        id = id,
        title = id,
        type = type,
        status = status,
        rating = rating,
        watchedAt = watchedAt,
        releaseYear = releaseYear,
        posterUrl = posterUrl,
    )

    private fun ids(list: List<Media>) = list.map { it.id }

    // --- Coups de cœur ---

    @Test
    fun `huit contenus a cinq etoiles donnent huit coups de coeur`() {
        val media = (1..8).map { media("m$it", rating = 5, watchedAt = at(1, it)) }
        assertEquals(8, selectFavorites(media, 2026, MediaType.FILM).size)
    }

    @Test
    fun `douze contenus a cinq etoiles gardent les dix vus le plus recemment`() {
        val media = (1..12).map { media("m$it", rating = 5, watchedAt = at(1, it)) }
        val favorites = selectFavorites(media, 2026, MediaType.FILM)
        assertEquals((12 downTo 3).map { "m$it" }, ids(favorites))
    }

    @Test
    fun `un cinq etoiles et trois quatre etoiles donnent le cinq etoiles puis les deux quatre les plus recents`() {
        val media = listOf(
            media("cinq", rating = 5, watchedAt = at(1, 1)),
            media("quatre-vieux", rating = 4, watchedAt = at(2, 1)),
            media("quatre-recent", rating = 4, watchedAt = at(4, 1)),
            media("quatre-milieu", rating = 4, watchedAt = at(3, 1)),
        )
        assertEquals(listOf("cinq", "quatre-recent", "quatre-milieu"), ids(selectFavorites(media, 2026, MediaType.FILM)))
    }

    @Test
    fun `deux cinq etoiles sont completes par un seul quatre etoiles`() {
        val media = listOf(
            media("cinq-a", rating = 5, watchedAt = at(1, 1)),
            media("cinq-b", rating = 5, watchedAt = at(1, 2)),
            media("quatre-a", rating = 4, watchedAt = at(2, 1)),
            media("quatre-b", rating = 4, watchedAt = at(3, 1)),
        )
        assertEquals(listOf("cinq-b", "cinq-a", "quatre-b"), ids(selectFavorites(media, 2026, MediaType.FILM)))
    }

    @Test
    fun `trois cinq etoiles ne sont pas completes par des quatre etoiles`() {
        val media = (1..3).map { media("cinq$it", rating = 5, watchedAt = at(1, it)) } +
            media("quatre", rating = 4)
        assertEquals(3, selectFavorites(media, 2026, MediaType.FILM).size)
    }

    @Test
    fun `sans cinq etoiles cinq contenus a quatre etoiles donnent trois contenus`() {
        val media = (1..5).map { media("q$it", rating = 4, watchedAt = at(1, it)) }
        val favorites = selectFavorites(media, 2026, MediaType.FILM)
        assertEquals(listOf("q5", "q4", "q3"), ids(favorites))
        assertTrue(favorites.all { it.rating == 4 })
    }

    @Test
    fun `aucun contenu note quatre ou cinq donne une liste vide`() {
        val media = listOf(
            media("trois", rating = 3),
            media("deux", rating = 2),
            media("non-note", rating = null),
        )
        assertTrue(selectFavorites(media, 2026, MediaType.FILM).isEmpty())
    }

    @Test
    fun `les contenus non notes d'une autre annee d'un autre type ou non vus sont ignores`() {
        val media = listOf(
            media("ok", rating = 5),
            media("autre-annee", rating = 5, watchedAt = at(6, 15, year = 2025)),
            media("autre-type", type = MediaType.SERIE, rating = 5),
            media("en-cours", rating = 5, status = WatchStatus.EN_COURS, watchedAt = null),
            media("a-voir", rating = 5, status = WatchStatus.A_VOIR, watchedAt = null),
            media("sans-date", rating = 5, watchedAt = null),
            media("non-note", rating = null),
        )
        assertEquals(listOf("ok"), ids(selectFavorites(media, 2026, MediaType.FILM)))
    }

    @Test
    fun `une note modifiee change la selection`() {
        val before = listOf(media("a", rating = 5, watchedAt = at(1, 1)), media("b", rating = 4, watchedAt = at(1, 2)))
        assertEquals(listOf("a", "b"), ids(selectFavorites(before, 2026, MediaType.FILM)))

        val after = before.map { if (it.id == "a") it.copy(rating = 2) else it }
        assertEquals(listOf("b"), ids(selectFavorites(after, 2026, MediaType.FILM)))
    }

    @Test
    fun `la grille passe a deux colonnes jusqu'a quatre contenus`() {
        assertEquals(2, favoritesGridColumns(1))
        assertEquals(2, favoritesGridColumns(4))
        assertEquals(3, favoritesGridColumns(5))
        assertEquals(3, favoritesGridColumns(10))
    }

    // --- Dates approximatives ---

    @Test
    fun `le premier juillet a midi pile est approximatif`() {
        assertTrue(isApproximateWatchedAt(ZonedDateTime.of(2024, 7, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `midi une seconde ou un autre jour ne sont pas approximatifs`() {
        assertFalse(isApproximateWatchedAt(ZonedDateTime.of(2024, 7, 1, 12, 0, 1, 0, zone).toInstant().toEpochMilli(), zone))
        assertFalse(isApproximateWatchedAt(ZonedDateTime.of(2024, 7, 1, 12, 0, 0, 1_000_000, zone).toInstant().toEpochMilli(), zone))
        assertFalse(isApproximateWatchedAt(ZonedDateTime.of(2024, 7, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
        assertFalse(isApproximateWatchedAt(ZonedDateTime.of(2024, 6, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `l'approximation depend du fuseau`() {
        val paris = ZoneId.of("Europe/Paris")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val parisNoon = ZonedDateTime.of(2024, 7, 1, 12, 0, 0, 0, paris).toInstant().toEpochMilli()
        assertTrue(isApproximateWatchedAt(parisNoon, paris))
        assertFalse(isApproximateWatchedAt(parisNoon, tokyo))
    }

    // --- Faits de l'année ---

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `premier et dernier contenus ignorent les dates approximatives`() {
        val media = listOf(
            media("approx", watchedAt = approximate(paris)),
            media("debut", watchedAt = at(1, 12, zone = paris)),
            media("fin", watchedAt = at(11, 30, zone = paris)),
            media("milieu", watchedAt = at(5, 5, zone = paris)),
        )
        val facts = computeRecapFacts(media, 2026, paris)
        assertEquals("debut", facts.first?.media?.id)
        assertEquals(LocalDate.of(2026, 1, 12), facts.first?.date)
        assertEquals("fin", facts.last?.media?.id)
        assertEquals(LocalDate.of(2026, 11, 30), facts.last?.date)
    }

    @Test
    fun `un seul contenu a date exacte n'est que le premier`() {
        val media = listOf(media("seul", watchedAt = at(3, 3, zone = paris)))
        val facts = computeRecapFacts(media, 2026, paris)
        assertEquals("seul", facts.first?.media?.id)
        assertNull(facts.last)
    }

    @Test
    fun `le mois le plus charge ignore les dates approximatives`() {
        val media = listOf(
            media("j1", watchedAt = approximate(paris)),
            media("j2", watchedAt = approximate(paris)),
            media("j3", watchedAt = approximate(paris)),
            media("mars-a", watchedAt = at(3, 2, zone = paris)),
            media("mars-b", watchedAt = at(3, 20, zone = paris)),
            media("avril", watchedAt = at(4, 9, zone = paris)),
        )
        assertEquals(BusiestMonth(Month.MARCH, 2), computeRecapFacts(media, 2026, paris).busiestMonth)
    }

    @Test
    fun `a egalite de mois le plus recent l'emporte`() {
        val media = listOf(
            media("jan", watchedAt = at(1, 9, zone = paris)),
            media("mars", watchedAt = at(3, 9, zone = paris)),
            media("oct", watchedAt = at(10, 9, zone = paris)),
        )
        assertEquals(BusiestMonth(Month.OCTOBER, 1), computeRecapFacts(media, 2026, paris).busiestMonth)
    }

    @Test
    fun `le mois se lit dans le fuseau fourni`() {
        val instant = ZonedDateTime.of(2026, 3, 31, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val media = listOf(media("limite", watchedAt = instant))
        val inParis = computeRecapFacts(media, 2026, ZoneId.of("Europe/Paris"))
        val inLosAngeles = computeRecapFacts(media, 2026, ZoneId.of("America/Los_Angeles"))
        assertEquals(Month.APRIL, inParis.busiestMonth?.month)
        assertEquals(Month.MARCH, inLosAngeles.busiestMonth?.month)
    }

    @Test
    fun `quand tout est approximatif premier dernier et mois sont omis`() {
        val media = listOf(
            media("a", watchedAt = approximate(paris), releaseYear = 1999),
            media("b", watchedAt = approximate(paris), releaseYear = 2010),
        )
        val facts = computeRecapFacts(media, 2026, paris)
        assertNull(facts.first)
        assertNull(facts.last)
        assertNull(facts.busiestMonth)
        assertEquals("a", facts.oldest?.id)
    }

    @Test
    fun `le contenu le plus ancien ignore les annees de sortie inconnues`() {
        val media = listOf(
            media("inconnu", releaseYear = null),
            media("recent", releaseYear = 2020),
            media("ancien", releaseYear = 1982),
        )
        assertEquals("ancien", computeRecapFacts(media, 2026, paris).oldest?.id)
    }

    @Test
    fun `sans aucune annee de sortie le contenu le plus ancien est omis`() {
        val media = listOf(media("a", releaseYear = null), media("b", releaseYear = null))
        assertNull(computeRecapFacts(media, 2026, paris).oldest)
    }

    @Test
    fun `a annee de sortie egale le contenu le plus ancien est le plus recemment vu`() {
        val media = listOf(
            media("vu-tot", watchedAt = at(2, 1), releaseYear = 1990),
            media("vu-tard", watchedAt = at(9, 1), releaseYear = 1990),
        )
        assertEquals("vu-tard", computeRecapFacts(media, 2026, paris).oldest?.id)
    }

    @Test
    fun `les faits ignorent les autres annees et les contenus non vus`() {
        val media = listOf(
            media("autre-annee", watchedAt = at(1, 2, year = 2025, zone = paris), releaseYear = 1950),
            media("en-cours", status = WatchStatus.EN_COURS, watchedAt = null, releaseYear = 1940),
            media("ok", watchedAt = at(5, 5, zone = paris), releaseYear = 2001),
        )
        val facts = computeRecapFacts(media, 2026, paris)
        assertEquals("ok", facts.first?.media?.id)
        assertEquals("ok", facts.oldest?.id)
    }

    @Test
    fun `les dates sont formatees en francais`() {
        assertEquals("12 janvier", formatRecapDate(LocalDate.of(2026, 1, 12)))
        assertEquals("1er mars", formatRecapDate(LocalDate.of(2026, 3, 1)))
        assertEquals("31 décembre", formatRecapDate(LocalDate.of(2026, 12, 31)))
        assertEquals("février", Month.FEBRUARY.frenchName())
    }

    // --- Liste des slides ---

    @Test
    fun `sans contenu vu dans l'annee il n'y a aucune slide`() {
        val media = listOf(media("ancien", watchedAt = at(6, 15, year = 2025)), media("a-voir", status = WatchStatus.A_VOIR, watchedAt = null))
        assertTrue(buildRecapSlides(media, 2026, paris).isEmpty())
        assertTrue(buildRecapSlides(emptyList(), 2026, paris).isEmpty())
    }

    @Test
    fun `les slides de type sont omises sans coup de coeur`() {
        val media = listOf(
            media("film", type = MediaType.FILM, rating = 3),
            media("serie", type = MediaType.SERIE, rating = null),
        )
        val slides = buildRecapSlides(media, 2026, paris)
        assertTrue(slides.none { it is RecapSlide.TypeFavorites })
        assertEquals(listOf("total", "facts", "mosaic"), slides.map { it.key })
    }

    @Test
    fun `les faits sont omis quand aucun fait n'existe`() {
        val media = listOf(media("film", watchedAt = approximate(paris), releaseYear = null))
        val slides = buildRecapSlides(media, 2026, paris)
        assertEquals(listOf("total", "mosaic"), slides.map { it.key })
    }

    @Test
    fun `la mosaique est presente des qu'il y a un contenu`() {
        val slides = buildRecapSlides(listOf(media("seul", watchedAt = approximate(paris))), 2026, paris)
        assertTrue(slides.last() is RecapSlide.Mosaic)
        assertEquals(1, (slides.last() as RecapSlide.Mosaic).media.size)
    }

    @Test
    fun `l'ordre est total puis types puis faits puis mosaique`() {
        val media = listOf(
            media("anime", type = MediaType.ANIME, rating = 5),
            media("film", type = MediaType.FILM, rating = 4),
            media("serie", type = MediaType.SERIE, rating = 5),
        )
        val slides = buildRecapSlides(media, 2026, paris)
        assertEquals(
            listOf("total", "type-FILM", "type-SERIE", "type-ANIME", "facts", "mosaic"),
            slides.map { it.key },
        )
    }

    @Test
    fun `le total et les compteurs viennent des contenus vus de l'annee`() {
        val media = listOf(
            media("f1", type = MediaType.FILM, rating = 5),
            media("f2", type = MediaType.FILM),
            media("s1", type = MediaType.SERIE),
            media("vieux", type = MediaType.FILM, watchedAt = at(6, 15, year = 2025)),
        )
        val total = buildRecapSlides(media, 2026, paris).first() as RecapSlide.Total
        assertEquals(3, total.total)
        assertEquals(mapOf(MediaType.FILM to 2, MediaType.SERIE to 1, MediaType.ANIME to 0), total.countsByType)
        val film = buildRecapSlides(media, 2026, paris).filterIsInstance<RecapSlide.TypeFavorites>().single()
        assertEquals(2, film.watchedCount)
        assertEquals(listOf("f1"), ids(film.favorites))
        assertEquals(2, film.columns)
    }

    // --- Mosaïque ---

    @Test
    fun `la mosaique est triee par date de visionnage croissante`() {
        val media = listOf(
            media("c", watchedAt = at(9, 1)),
            media("a", watchedAt = at(1, 1)),
            media("b", watchedAt = at(5, 1)),
            media("autre-annee", watchedAt = at(1, 1, year = 2025)),
            media("a-voir", status = WatchStatus.A_VOIR, watchedAt = null),
        )
        assertEquals(listOf("a", "b", "c"), ids(mosaicMedia(media, 2026)))
    }

    @Test
    fun `la mosaique melange tous les types`() {
        val media = listOf(
            media("film", type = MediaType.FILM, watchedAt = at(1, 1)),
            media("serie", type = MediaType.SERIE, watchedAt = at(2, 1)),
            media("anime", type = MediaType.ANIME, watchedAt = at(3, 1)),
        )
        assertEquals(listOf("film", "serie", "anime"), ids(mosaicMedia(media, 2026)))
        assertNotNull(buildRecapSlides(media, 2026, paris).last())
    }
}
