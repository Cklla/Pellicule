package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.EpisodeReminder
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeReminderRepositoryImplTest {

    private val repository = EpisodeReminderRepositoryImpl(FakeEpisodeReminderDao())

    @Test
    fun `aucun rappel n'est actif par defaut`() = runTest {
        assertFalse(repository.observeEnabled("m1").first())
        assertTrue(repository.getEnabledReminders().isEmpty())
    }

    @Test
    fun `activer puis eteindre un rappel`() = runTest {
        assertTrue(repository.setEnabled("m1", true) is Resource.Success)
        assertTrue(repository.observeEnabled("m1").first())
        assertEquals(listOf(EpisodeReminder("m1", null)), repository.getEnabledReminders())

        repository.setEnabled("m1", false)

        assertFalse(repository.observeEnabled("m1").first())
        assertTrue(repository.getEnabledReminders().isEmpty())
    }

    @Test
    fun `le dernier episode notifie est retenu`() = runTest {
        repository.setEnabled("m1", true)

        repository.markNotified("m1", EpisodeKey(2, 5))

        assertEquals(EpisodeKey(2, 5), repository.getEnabledReminders().single().lastNotified)
    }

    @Test
    fun `eteindre puis rallumer un rappel conserve le dernier episode notifie`() = runTest {
        repository.setEnabled("m1", true)
        repository.markNotified("m1", EpisodeKey(2, 5))

        repository.setEnabled("m1", false)
        repository.setEnabled("m1", true)

        assertEquals(EpisodeKey(2, 5), repository.getEnabledReminders().single().lastNotified)
    }

    @Test
    fun `les rappels de plusieurs contenus restent independants`() = runTest {
        repository.setEnabled("m1", true)
        repository.setEnabled("m2", true)
        repository.markNotified("m1", EpisodeKey(1, 3))

        val reminders = repository.getEnabledReminders().associateBy { it.mediaId }

        assertEquals(EpisodeKey(1, 3), reminders.getValue("m1").lastNotified)
        assertEquals(null, reminders.getValue("m2").lastNotified)
    }
}
