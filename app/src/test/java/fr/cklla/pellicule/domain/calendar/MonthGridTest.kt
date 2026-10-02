package fr.cklla.pellicule.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthGridTest {

    private fun MonthGrid.days(): List<Int> = weeks.flatten().filterNotNull()

    @Test
    fun `fevrier de 28 jours qui commence un lundi tient sur 4 semaines pleines`() {
        // Février 2027 : 1er février = lundi, 28 jours.
        val grid = buildMonthGrid(2027, 2)

        assertEquals(4, grid.weeks.size)
        assertEquals(1, grid.weeks.first().first())
        assertEquals(28, grid.weeks.last().last())
        assertEquals((1..28).toList(), grid.days())
    }

    @Test
    fun `fevrier bissextile compte 29 jours`() {
        // Février 2028 : 1er = mardi.
        val grid = buildMonthGrid(2028, 2)

        assertEquals(29, grid.days().size)
        assertNull(grid.weeks.first()[0])
        assertEquals(1, grid.weeks.first()[1])
    }

    @Test
    fun `mois de 30 jours`() {
        // Avril 2026 : 1er = mercredi, 30 jours.
        val grid = buildMonthGrid(2026, 4)

        assertEquals((1..30).toList(), grid.days())
        assertEquals(listOf(null, null, 1, 2, 3, 4, 5), grid.weeks.first())
    }

    @Test
    fun `mois de 31 jours qui deborde sur une sixieme semaine`() {
        // Mars 2025 : 1er = samedi, 31 jours -> 6 lignes.
        val grid = buildMonthGrid(2025, 3)

        assertEquals(6, grid.weeks.size)
        assertEquals((1..31).toList(), grid.days())
        assertEquals(listOf(null, null, null, null, null, 1, 2), grid.weeks.first())
        assertEquals(listOf(31, null, null, null, null, null, null), grid.weeks.last())
    }

    @Test
    fun `la semaine commence le lundi et chaque ligne a 7 cases`() {
        // Octobre 2026 : 1er = jeudi, donc 3 cases vides avant (lundi, mardi, mercredi).
        val grid = buildMonthGrid(2026, 10)

        assertEquals(listOf(null, null, null, 1, 2, 3, 4), grid.weeks.first())
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), grid.weeks[1])
        assertEquals(true, grid.weeks.all { it.size == 7 })
        assertEquals(31, grid.days().size)
    }

    @Test
    fun `un mois qui commence un dimanche a six cases vides avant le 1er`() {
        // Février 2026 : 1er = dimanche.
        val grid = buildMonthGrid(2026, 2)

        assertEquals(listOf(null, null, null, null, null, null, 1), grid.weeks.first())
    }

    @Test
    fun `le jour de la semaine est calcule depuis lundi`() {
        assertEquals(3, dayOfWeekMondayFirst(2026, 10, 8)) // jeudi 8 octobre 2026
        assertEquals(0, dayOfWeekMondayFirst(2026, 10, 5)) // lundi
        assertEquals(6, dayOfWeekMondayFirst(2026, 10, 11)) // dimanche
    }

    @Test
    fun `daysInMonth gere les annees seculaires`() {
        assertEquals(29, daysInMonth(2000, 2))
        assertEquals(28, daysInMonth(2100, 2))
    }
}
