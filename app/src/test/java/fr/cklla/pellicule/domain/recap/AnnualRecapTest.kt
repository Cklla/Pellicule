package fr.cklla.pellicule.domain.recap

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnualRecapTest {

    @Test
    fun `24 decembre ne declenche pas de recap`() {
        assertNull(recapTargetYear(LocalDate.of(2026, 12, 24)))
    }

    @Test
    fun `25 decembre cible l'annee en cours`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2026, 12, 25)))
    }

    @Test
    fun `31 decembre cible toujours l'annee en cours`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `1er janvier bascule sur l'annee precedente`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `31 janvier est le dernier jour du recap`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2027, 1, 31)))
    }

    @Test
    fun `1er fevrier ne declenche plus de recap`() {
        assertNull(recapTargetYear(LocalDate.of(2027, 2, 1)))
    }

    @Test
    fun `hors fenetre, pas de notification meme si jamais notifie`() {
        assertNull(recapYearToNotify(LocalDate.of(2026, 6, 15), alreadyNotified = { false }))
    }

    @Test
    fun `dans la fenetre et jamais notifie pour l'annee, notifie`() {
        assertEquals(2026, recapYearToNotify(LocalDate.of(2026, 12, 25), alreadyNotified = { false }))
    }

    @Test
    fun `annee deja notifiee, ne renotifie pas`() {
        assertNull(recapYearToNotify(LocalDate.of(2026, 12, 28), alreadyNotified = { it == 2026 }))
    }

    @Test
    fun `une autre annee notifiee ne bloque pas l'annee ciblee`() {
        assertEquals(2026, recapYearToNotify(LocalDate.of(2026, 12, 25), alreadyNotified = { it == 2025 }))
    }

    @Test
    fun `en janvier le flag s'applique a l'annee precedente`() {
        assertEquals(2026, recapYearToNotify(LocalDate.of(2027, 1, 15), alreadyNotified = { false }))
        assertNull(recapYearToNotify(LocalDate.of(2027, 1, 15), alreadyNotified = { it == 2026 }))
    }
}
