package fr.cklla.pellicule.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeFormatTest {

    @Test
    fun `moins d'une heure s'affiche en minutes seules`() {
        assertEquals("45 min", formatRuntime(45))
    }

    @Test
    fun `une duree ronde s'affiche en heures seules`() {
        assertEquals("2 h", formatRuntime(120))
    }

    @Test
    fun `heures et minutes sont separees par des espaces insecables`() {
        assertEquals("2 h 16 min", formatRuntime(136))
    }

    @Test
    fun `les minutes ne sont pas completees par un zero`() {
        assertEquals("1 h 5 min", formatRuntime(65))
    }
}
