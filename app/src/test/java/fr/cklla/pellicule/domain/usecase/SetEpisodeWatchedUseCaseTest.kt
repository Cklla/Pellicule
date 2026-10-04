package fr.cklla.pellicule.domain.usecase

import fr.cklla.pellicule.data.repository.fakeEpisodeRepository
import fr.cklla.pellicule.data.repository.FakeJellyfinRepository
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.FakeTvShowInfoRepository
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.util.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEpisodeWatchedUseCaseTest {

    private val mediaRepository = fakeMediaRepository(FakeMediaDao())
    private val episodeRepository = fakeEpisodeRepository()
    private val tvShowRepository = FakeTvShowInfoRepository()
    private val jellyfinRepository = FakeJellyfinRepository()
    private val useCase = SetEpisodeWatchedUseCase(
        mediaRepository,
        episodeRepository,
        tvShowRepository,
        jellyfinRepository,
        TimeSource { 1_790_000_000_000L },
    )

    private fun connectJellyfin() = jellyfinRepository.setSession(
        JellyfinSession("https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"),
    )

    private fun show(status: TvShowStatus, counts: Map<Int, Int> = mapOf(1 to 2)) = TvShowInfo(
        tmdbId = 42,
        status = status,
        seasonEpisodeCounts = counts,
        lastAired = null,
        nextToAir = null,
    )

    private suspend fun addSeries(status: WatchStatus = WatchStatus.A_VOIR, type: MediaType = MediaType.SERIE): String {
        val result = mediaRepository.addMedia(Media(title = "Severance", type = type, status = status, tmdbId = 42))
        return (result as Resource.Success).data
    }

    private suspend fun watched(mediaId: String) = episodeRepository.observeWatchedEpisodes(mediaId).first()

    private suspend fun stored(mediaId: String) = mediaRepository.observeMediaById(mediaId).first()!!

    @Test
    fun `cocher un episode l'enregistre localement`() = runTest {
        val id = addSeries()

        useCase(id, EpisodeKey(1, 1), watched = true)

        assertEquals(setOf(EpisodeKey(1, 1)), watched(id))
    }

    @Test
    fun `decocher un episode le retire`() = runTest {
        val id = addSeries(WatchStatus.EN_COURS)
        useCase(id, EpisodeKey(1, 1), watched = true)

        useCase(id, EpisodeKey(1, 1), watched = false)

        assertTrue(watched(id).isEmpty())
    }

    @Test
    fun `le premier episode coche passe un contenu A voir a En cours`() = runTest {
        val id = addSeries(WatchStatus.A_VOIR)

        val result = useCase(id, EpisodeKey(1, 1), watched = true)

        assertEquals(WatchStatus.EN_COURS, result?.status)
        assertEquals(WatchStatus.EN_COURS, stored(id).status)
    }

    @Test
    fun `atteindre le dernier episode d'une serie terminee passe a Vu et pose watchedAt`() = runTest {
        tvShowRepository.put(show(TvShowStatus.TERMINEE))
        val id = addSeries(WatchStatus.EN_COURS)
        useCase(id, EpisodeKey(1, 1), watched = true)
        assertEquals(WatchStatus.EN_COURS, stored(id).status)

        val result = useCase(id, EpisodeKey(1, 2), watched = true)

        assertEquals(WatchStatus.VU, result?.status)
        assertNotNull("watchedAt est pose par la transition vers Vu", result?.watchedAt)
        assertEquals(WatchStatus.VU, stored(id).status)
        assertNotNull(stored(id).watchedAt)
    }

    @Test
    fun `une serie encore diffusee reste En cours meme a jour`() = runTest {
        tvShowRepository.put(show(TvShowStatus.EN_DIFFUSION))
        val id = addSeries(WatchStatus.EN_COURS)
        useCase(id, EpisodeKey(1, 1), watched = true)

        val result = useCase(id, EpisodeKey(1, 2), watched = true)

        assertEquals(WatchStatus.EN_COURS, result?.status)
        assertNull(stored(id).watchedAt)
    }

    @Test
    fun `sans cache de metadonnees le contenu ne passe jamais a Vu`() = runTest {
        val id = addSeries(WatchStatus.EN_COURS)

        val result = useCase(id, EpisodeKey(1, 2), watched = true)

        assertEquals(WatchStatus.EN_COURS, result?.status)
    }

    @Test
    fun `decocher un episode d'un contenu Vu le repasse En cours et efface watchedAt`() = runTest {
        tvShowRepository.put(show(TvShowStatus.TERMINEE))
        val id = addSeries(WatchStatus.EN_COURS)
        useCase(id, EpisodeKey(1, 1), watched = true)
        useCase(id, EpisodeKey(1, 2), watched = true)
        assertEquals(WatchStatus.VU, stored(id).status)

        val result = useCase(id, EpisodeKey(1, 2), watched = false)

        assertEquals(WatchStatus.EN_COURS, result?.status)
        assertNull(stored(id).watchedAt)
    }

    @Test
    fun `cocher ne retrograde pas un contenu deja Vu`() = runTest {
        val id = addSeries(WatchStatus.VU)

        val result = useCase(id, EpisodeKey(1, 1), watched = true)

        assertEquals(WatchStatus.VU, result?.status)
    }

    @Test
    fun `l'episode est pousse vers Jellyfin avec le statut a jour`() = runTest {
        connectJellyfin()
        val id = addSeries(WatchStatus.A_VOIR)

        useCase(id, EpisodeKey(1, 1), watched = true)

        assertEquals(1, jellyfinRepository.pushedEpisodes.size)
        val (media, episode, watched) = jellyfinRepository.pushedEpisodes.single()
        assertEquals(EpisodeKey(1, 1), episode)
        assertTrue(watched)
        assertEquals("le contenu pousse porte le statut deja recalcule", WatchStatus.EN_COURS, media.status)
    }

    @Test
    fun `un decochage est pousse comme non vu`() = runTest {
        connectJellyfin()
        val id = addSeries(WatchStatus.EN_COURS)
        useCase(id, EpisodeKey(1, 1), watched = true)

        useCase(id, EpisodeKey(1, 1), watched = false)

        assertFalse(jellyfinRepository.pushedEpisodes.last().third)
    }

    @Test
    fun `sans session Jellyfin la partie locale aboutit et rien n'est pousse`() = runTest {
        val id = addSeries(WatchStatus.A_VOIR)

        val result = useCase(id, EpisodeKey(1, 1), watched = true)

        assertTrue(jellyfinRepository.pushedEpisodes.isEmpty())
        assertEquals(setOf(EpisodeKey(1, 1)), watched(id))
        assertEquals(WatchStatus.EN_COURS, result?.status)
    }

    @Test
    fun `la partie locale est notifiee avant la poussee Jellyfin`() = runTest {
        connectJellyfin()
        val id = addSeries(WatchStatus.A_VOIR)
        var pushedWhenNotified = -1

        useCase(id, EpisodeKey(1, 1), watched = true) {
            pushedWhenNotified = jellyfinRepository.pushedEpisodes.size
        }

        assertEquals(0, pushedWhenNotified)
        assertEquals(1, jellyfinRepository.pushedEpisodes.size)
    }

    @Test
    fun `un film n'a pas d'episodes`() = runTest {
        val id = addSeries(type = MediaType.FILM)

        assertNull(useCase(id, EpisodeKey(1, 1), watched = true))
        assertTrue(watched(id).isEmpty())
    }

    @Test
    fun `un contenu inconnu est ignore`() = runTest {
        assertNull(useCase("absent", EpisodeKey(1, 1), watched = true))
    }
}
