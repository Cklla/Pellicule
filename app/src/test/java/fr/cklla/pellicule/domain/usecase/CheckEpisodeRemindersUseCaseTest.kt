package fr.cklla.pellicule.domain.usecase

import fr.cklla.pellicule.data.repository.EpisodeReminderRepositoryImpl
import fr.cklla.pellicule.data.repository.FakeEpisodeReminderDao
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.FakeTvShowInfoRepository
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.notification.EpisodeNotifier
import fr.cklla.pellicule.domain.util.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckEpisodeRemindersUseCaseTest {

    // Midi UTC : le même jour civil dans tous les fuseaux de métropole et d'outre-mer courants.
    private val dayBefore = 1_791_374_400_000L // 2026-10-07
    private val airDay = 1_791_460_800_000L // 2026-10-08
    private val dayAfter = 1_791_547_200_000L // 2026-10-09

    private var now = airDay
    private val mediaRepository = fakeMediaRepository(FakeMediaDao())
    private val reminders = EpisodeReminderRepositoryImpl(FakeEpisodeReminderDao())
    private val tvShows = FakeTvShowInfoRepository()
    private val notified = mutableListOf<Triple<String, String, EpisodeKey>>()
    private var canNotify = true
    private val notifier = EpisodeNotifier { mediaId, title, episode ->
        if (canNotify) notified.add(Triple(mediaId, title, episode))
        canNotify
    }

    private val useCase get() = CheckEpisodeRemindersUseCase(reminders, mediaRepository, tvShows, notifier, TimeSource { now })

    private suspend fun trackedShow(tmdbId: Long = 42, title: String = "Severance"): String {
        val id = (mediaRepository.addMedia(
            Media(title = title, type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = tmdbId),
        ) as Resource.Success).data
        reminders.setEnabled(id, true)
        return id
    }

    private fun show(
        tmdbId: Long = 42,
        airDate: String? = "2026-10-08",
        status: TvShowStatus = TvShowStatus.EN_DIFFUSION,
        key: EpisodeKey = EpisodeKey(2, 5),
    ) = TvShowInfo(
        tmdbId = tmdbId,
        status = status,
        seasonEpisodeCounts = emptyMap(),
        lastAired = null,
        nextToAir = EpisodeAirInfo(key, AirDate.parse(airDate), title = null),
    )

    @Test
    fun `le jour de la diffusion, l'episode est notifie et retenu`() = runTest {
        val id = trackedShow()
        tvShows.put(show())

        val count = useCase()

        assertEquals(1, count)
        assertEquals(listOf(Triple(id, "Severance", EpisodeKey(2, 5))), notified)
        assertEquals(EpisodeKey(2, 5), reminders.getEnabledReminders().single().lastNotified)
    }

    @Test
    fun `une seule notification par episode meme si le controle repasse`() = runTest {
        trackedShow()
        tvShows.put(show())

        useCase()
        useCase()

        assertEquals(1, notified.size)
    }

    @Test
    fun `la veille de la diffusion, rien n'est notifie`() = runTest {
        trackedShow()
        tvShows.put(show())
        now = dayBefore

        assertEquals(0, useCase())
        assertTrue(notified.isEmpty())
    }

    @Test
    fun `le lendemain de la diffusion, rien n'est rattrape`() = runTest {
        trackedShow()
        tvShows.put(show())
        now = dayAfter

        assertEquals(0, useCase())
        assertTrue(notified.isEmpty())
    }

    @Test
    fun `une serie terminee n'est pas notifiee`() = runTest {
        trackedShow()
        tvShows.put(show(status = TvShowStatus.TERMINEE))

        assertEquals(0, useCase())
    }

    @Test
    fun `un contenu retire du suivi n'est ni notifie ni interroge sur TMDB`() = runTest {
        val id = trackedShow()
        tvShows.put(show())
        mediaRepository.deleteMedia(id)

        assertEquals(0, useCase())
        assertTrue(notified.isEmpty())
        assertTrue(tvShows.dailyRefreshedShows.isEmpty())
    }

    @Test
    fun `un rappel eteint n'est ni notifie ni interroge sur TMDB`() = runTest {
        val id = trackedShow()
        tvShows.put(show())
        reminders.setEnabled(id, false)

        assertEquals(0, useCase())
        assertTrue(tvShows.dailyRefreshedShows.isEmpty())
    }

    @Test
    fun `seules les series avec un rappel actif sont rafraichies`() = runTest {
        trackedShow(tmdbId = 42)
        mediaRepository.addMedia(Media(title = "Sans rappel", type = MediaType.SERIE, status = WatchStatus.A_VOIR, tmdbId = 7))
        tvShows.put(show(tmdbId = 42))

        useCase()

        assertEquals(listOf(42L), tvShows.dailyRefreshedShows)
    }

    @Test
    fun `sans permission, l'episode n'est pas marque notifie et sera retente`() = runTest {
        trackedShow()
        tvShows.put(show())
        canNotify = false

        assertEquals(0, useCase())
        assertEquals(null, reminders.getEnabledReminders().single().lastNotified)

        canNotify = true
        assertEquals(1, useCase())
    }

    @Test
    fun `un contenu sans donnees en cache ne bloque pas les autres`() = runTest {
        trackedShow(tmdbId = 1, title = "Sans cache")
        val id = trackedShow(tmdbId = 42, title = "Severance")
        tvShows.put(show(tmdbId = 42))

        assertEquals(1, useCase())
        assertEquals(id, notified.single().first)
    }

    @Test
    fun `un echec de notification sur un contenu n'empeche pas les suivants`() = runTest {
        trackedShow(tmdbId = 1, title = "Premier")
        val second = trackedShow(tmdbId = 2, title = "Second")
        tvShows.put(show(tmdbId = 1))
        tvShows.put(show(tmdbId = 2))
        val failingFirst = EpisodeNotifier { mediaId, title, episode ->
            if (title == "Premier") error("notification impossible")
            notified.add(Triple(mediaId, title, episode))
            true
        }

        val count = CheckEpisodeRemindersUseCase(reminders, mediaRepository, tvShows, failingFirst, TimeSource { now })()

        assertEquals(1, count)
        assertEquals(second, notified.single().first)
    }

    @Test
    fun `sans aucun rappel actif, rien n'est interroge`() = runTest {
        assertEquals(0, useCase())
        assertTrue(tvShows.dailyRefreshedShows.isEmpty())
    }
}
