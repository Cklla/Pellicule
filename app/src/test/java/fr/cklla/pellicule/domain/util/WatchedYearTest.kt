package fr.cklla.pellicule.domain.util

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.bibliotheque.watchedYear
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchedYearTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12, zone: ZoneId = ZoneOffset.UTC): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    // 2026-10-04 à midi, fuseau explicite.
    private fun clock(zone: ZoneId = ZoneOffset.UTC, now: Long = millis(2026, 10, 4, zone = zone)) =
        object : TimeSource {
            override fun nowMillis() = now
            override val zone: ZoneId = zone
        }

    private fun mediaWatchedAt(millis: Long) =
        Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, watchedAt = millis)

    @Test
    fun `une annee passee donne le 1er juillet a midi`() {
        val result = watchedAtForYear(2024, currentWatchedAt = null, releaseYear = 2021, timeSource = clock())

        assertEquals(millis(2024, 7, 1), result)
    }

    @Test
    fun `une annee passee suit le fuseau de l horloge`() {
        val paris = ZoneId.of("Europe/Paris")

        val result = watchedAtForYear(2025, currentWatchedAt = null, releaseYear = null, timeSource = clock(paris))

        assertEquals(millis(2025, 7, 1, zone = paris), result)
    }

    @Test
    fun `l annee en cours ne change rien si la date est deja dans l annee`() {
        val existing = millis(2026, 2, 14)

        val result = watchedAtForYear(2026, currentWatchedAt = existing, releaseYear = 2020, timeSource = clock())

        assertEquals(existing, result)
    }

    @Test
    fun `l annee en cours depuis une annee passee donne l instant present`() {
        val timeSource = clock()

        val result = watchedAtForYear(2026, currentWatchedAt = millis(2024, 7, 1), releaseYear = 2020, timeSource = timeSource)

        assertEquals(timeSource.nowMillis(), result)
    }

    @Test
    fun `l annee en cours sans date connue donne l instant present`() {
        val timeSource = clock()

        val result = watchedAtForYear(2026, currentWatchedAt = null, releaseYear = null, timeSource = timeSource)

        assertEquals(timeSource.nowMillis(), result)
    }

    @Test
    fun `reprendre l annee deja enregistree conserve la date exacte`() {
        val existing = millis(2024, 3, 9, hour = 21)

        val result = watchedAtForYear(2024, currentWatchedAt = existing, releaseYear = 2020, timeSource = clock())

        assertEquals(existing, result)
    }

    @Test
    fun `une annee future est refusee`() {
        assertNull(watchedAtForYear(2027, currentWatchedAt = null, releaseYear = 2020, timeSource = clock()))
    }

    @Test
    fun `une annee avant la sortie est refusee`() {
        assertNull(watchedAtForYear(2019, currentWatchedAt = null, releaseYear = 2020, timeSource = clock()))
        assertEquals(millis(2020, 7, 1), watchedAtForYear(2020, currentWatchedAt = null, releaseYear = 2020, timeSource = clock()))
    }

    @Test
    fun `sans annee de sortie la borne basse est 1950`() {
        assertNull(watchedAtForYear(1949, currentWatchedAt = null, releaseYear = null, timeSource = clock()))
        assertEquals(millis(1950, 7, 1), watchedAtForYear(1950, currentWatchedAt = null, releaseYear = null, timeSource = clock()))
    }

    @Test
    fun `un contenu pas encore sorti reste rattache a l annee en cours`() {
        val timeSource = clock()

        assertEquals(listOf(2026), selectableWatchedYears(releaseYear = 2027, timeSource = timeSource))
        assertEquals(timeSource.nowMillis(), watchedAtForYear(2026, currentWatchedAt = null, releaseYear = 2027, timeSource = timeSource))
    }

    @Test
    fun `les annees proposees vont de l annee en cours a l annee de sortie`() {
        assertEquals(listOf(2026, 2025, 2024, 2023), selectableWatchedYears(releaseYear = 2023, timeSource = clock()))
    }

    @Test
    fun `les annees proposees descendent jusqu a 1950 sans annee de sortie`() {
        val years = selectableWatchedYears(releaseYear = null, timeSource = clock())

        assertEquals(2026, years.first())
        assertEquals(1950, years.last())
        assertEquals(2026 - 1950 + 1, years.size)
    }

    @Test
    fun `l annee de l instant present suit le fuseau de l horloge`() {
        // 2026-12-31 23:30 UTC : déjà 2027 à Paris.
        val now = millis(2026, 12, 31, hour = 23).plus(30 * 60_000)
        val paris = clock(ZoneId.of("Europe/Paris"), now)

        assertEquals(2027, selectableWatchedYears(releaseYear = 2025, timeSource = paris).first())
        assertEquals(2026, selectableWatchedYears(releaseYear = 2025, timeSource = clock(ZoneOffset.UTC, now)).first())
    }

    @Test
    fun `watchedYear relit toujours l annee choisie sous les fuseaux extremes`() {
        val original = TimeZone.getDefault()
        try {
            listOf(ZoneOffset.ofHours(-12), ZoneOffset.ofHours(14), ZoneOffset.UTC).forEach { offset ->
                TimeZone.setDefault(TimeZone.getTimeZone(offset))
                val timeSource = clock(ZoneId.systemDefault())

                listOf(1999, 2024, 2025).forEach { year ->
                    val watchedAt = watchedAtForYear(year, currentWatchedAt = null, releaseYear = null, timeSource = timeSource)!!

                    assertEquals("année $year sous $offset", year, watchedYear(mediaWatchedAt(watchedAt)))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `l horodatage du 1er juillet reste dans la bonne annee dans tous les fuseaux`() {
        val watchedAt = watchedAtForYear(2024, currentWatchedAt = null, releaseYear = null, timeSource = clock(ZoneOffset.UTC))!!

        (-12..14).forEach { hours ->
            val year = java.time.Instant.ofEpochMilli(watchedAt).atZone(ZoneOffset.ofHours(hours)).year
            assertEquals("UTC$hours", 2024, year)
        }
    }
}
