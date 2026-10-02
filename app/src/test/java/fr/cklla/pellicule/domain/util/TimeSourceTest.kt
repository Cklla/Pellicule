package fr.cklla.pellicule.domain.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSourceTest {

    // 2026-12-31 23:30 UTC
    private val timeSource = TimeSource { 1_798_759_800_000L }

    @Test
    fun `la date du jour suit le fuseau demande`() {
        assertEquals(LocalDate.of(2026, 12, 31), timeSource.today(ZoneId.of("UTC")))
        assertEquals(LocalDate.of(2027, 1, 1), timeSource.today(ZoneId.of("Europe/Paris")))
    }
}
