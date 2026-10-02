package fr.cklla.pellicule.domain.model

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirDateTest {

    @Test
    fun `parse accepte une date ISO et rejette le reste`() {
        assertEquals("2026-10-01", AirDate.parse("2026-10-01")?.iso)
        assertNull(AirDate.parse(null))
        assertNull(AirDate.parse(""))
        assertNull(AirDate.parse("01/10/2026"))
    }

    @Test
    fun `les dates se comparent dans l'ordre chronologique`() {
        assertTrue(AirDate.parse("2026-09-30")!! < AirDate.parse("2026-10-01")!!)
        assertTrue(AirDate.parse("2025-12-31")!! < AirDate.parse("2026-01-01")!!)
    }

    @Test
    fun `fromMillis lit le jour civil dans le fuseau donne`() {
        // 2026-10-01 23:30 UTC est déjà le 2 octobre à Paris (UTC+2 en été).
        val millis = 1_790_897_400_000L
        assertEquals("2026-10-01", AirDate.fromMillis(millis, TimeZone.getTimeZone("UTC")).iso)
        assertEquals("2026-10-02", AirDate.fromMillis(millis, TimeZone.getTimeZone("Europe/Paris")).iso)
    }

    @Test
    fun `format affiche jour et mois abrege`() {
        val formatted = AirDate.parse("2026-10-08")!!.format(Locale.FRENCH, TimeZone.getTimeZone("UTC"))

        assertTrue(formatted, formatted.startsWith("8 oct"))
    }
}
