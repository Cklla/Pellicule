package fr.cklla.pellicule.notification

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 60L * 60 * 1000

    @Test
    fun `avant l'heure cible, le delai vise le jour meme`() {
        // 2026-10-08 05:30 UTC -> 9 h le même jour : 3 h 30.
        assertEquals(3 * hour + hour / 2, delayUntilNextHour(1_791_437_400_000L, 9, utc))
    }

    @Test
    fun `apres l'heure cible, le delai vise le lendemain`() {
        // 2026-10-08 12:00 UTC -> 9 h le lendemain : 21 h.
        assertEquals(21 * hour, delayUntilNextHour(1_791_460_800_000L, 9, utc))
    }

    @Test
    fun `pile a l'heure cible, le delai vise le lendemain`() {
        // 2026-10-08 09:00 UTC exactement.
        assertEquals(24 * hour, delayUntilNextHour(1_791_450_000_000L, 9, utc))
    }

    @Test
    fun `l'heure est lue dans le fuseau demande`() {
        // 2026-10-08 05:30 UTC = 07:30 à Paris (UTC+2) -> 9 h locale dans 1 h 30.
        val paris = TimeZone.getTimeZone("Europe/Paris")

        assertEquals(hour + hour / 2, delayUntilNextHour(1_791_437_400_000L, 9, paris))
    }
}
