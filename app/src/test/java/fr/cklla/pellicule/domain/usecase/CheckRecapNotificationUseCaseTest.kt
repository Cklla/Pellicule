package fr.cklla.pellicule.domain.usecase

import fr.cklla.pellicule.domain.notification.RecapNotificationStore
import fr.cklla.pellicule.domain.notification.RecapNotifier
import fr.cklla.pellicule.domain.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckRecapNotificationUseCaseTest {

    private class FakeStore(val notified: MutableSet<Int> = mutableSetOf()) : RecapNotificationStore {
        override fun hasNotified(year: Int) = year in notified
        override fun markNotified(year: Int) {
            notified += year
        }
    }

    private class FakeNotifier(var canNotify: Boolean = true) : RecapNotifier {
        val notifiedYears = mutableListOf<Int>()
        override fun notify(year: Int): Boolean {
            if (canNotify) notifiedYears += year
            return canNotify
        }
    }

    // Midi UTC : la date locale reste celle voulue dans tous les fuseaux courants.
    private fun clockOn(date: LocalDate) = TimeSource { date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    private fun useCase(store: FakeStore, notifier: FakeNotifier, date: LocalDate) =
        CheckRecapNotificationUseCase(store, notifier, clockOn(date))

    @Test
    fun `notifie a la premiere execution dans la fenetre et memorise l'annee`() {
        val store = FakeStore()
        val notifier = FakeNotifier()

        useCase(store, notifier, LocalDate.of(2026, 12, 26))()

        assertEquals(listOf(2026), notifier.notifiedYears)
        assertTrue(store.hasNotified(2026))
    }

    @Test
    fun `ne renotifie pas les jours suivants de la meme fenetre`() {
        val store = FakeStore()
        val notifier = FakeNotifier()

        useCase(store, notifier, LocalDate.of(2026, 12, 26))()
        useCase(store, notifier, LocalDate.of(2026, 12, 27))()
        useCase(store, notifier, LocalDate.of(2027, 1, 10))()

        assertEquals(listOf(2026), notifier.notifiedYears)
    }

    @Test
    fun `hors fenetre, ne notifie rien`() {
        val store = FakeStore()
        val notifier = FakeNotifier()

        useCase(store, notifier, LocalDate.of(2026, 6, 15))()

        assertTrue(notifier.notifiedYears.isEmpty())
        assertFalse(store.hasNotified(2026))
    }

    @Test
    fun `sans permission, l'annee reste due et part une fois la permission accordee`() {
        val store = FakeStore()
        val notifier = FakeNotifier(canNotify = false)

        useCase(store, notifier, LocalDate.of(2026, 12, 26))()
        assertFalse(store.hasNotified(2026))

        notifier.canNotify = true
        useCase(store, notifier, LocalDate.of(2026, 12, 27))()

        assertEquals(listOf(2026), notifier.notifiedYears)
        assertTrue(store.hasNotified(2026))
    }

    @Test
    fun `l'annee suivante est notifiee meme si la precedente l'a ete`() {
        val store = FakeStore(notified = mutableSetOf(2026))
        val notifier = FakeNotifier()

        useCase(store, notifier, LocalDate.of(2027, 12, 25))()

        assertEquals(listOf(2027), notifier.notifiedYears)
    }
}
