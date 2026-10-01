package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeDto
import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeToAirDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonSummaryDto
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.util.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvShowInfoRepositoryImplTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private var now = 1_000_000_000_000L
    private val dao = FakeTvShowCacheDao()
    private val api = FakeTmdbApi().apply {
        tvStatus = "Returning Series"
        seasons = listOf(
            TmdbSeasonSummaryDto(seasonNumber = 0, name = "Spéciaux", episodeCount = 2),
            TmdbSeasonSummaryDto(seasonNumber = 1, name = "Saison 1", episodeCount = 10),
            TmdbSeasonSummaryDto(seasonNumber = 2, name = "Saison 2", episodeCount = 8),
        )
        lastEpisodeToAir = TmdbEpisodeToAirDto(seasonNumber = 2, episodeNumber = 4, name = "Quatre", airDate = "2026-09-24")
        nextEpisodeToAir = TmdbEpisodeToAirDto(seasonNumber = 2, episodeNumber = 5, name = "", airDate = "2026-10-08")
        episodes = listOf(
            TmdbEpisodeDto(seasonNumber = 2, episodeNumber = 5, name = "Cinq", airDate = "2026-10-08"),
            TmdbEpisodeDto(seasonNumber = 2, episodeNumber = 6, name = "", airDate = null),
        )
    }

    private fun repository(authRepository: FakeAuthRepository = FakeAuthRepository()) = TvShowInfoRepositoryImpl(
        dao = dao,
        tmdbApi = api,
        timeSource = TimeSource { now },
        authRepository = authRepository,
        repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test
    fun `le premier rafraichissement stocke statut, saisons et episodes charnieres`() = runTest {
        val repository = repository()

        repository.refreshIfStale(tmdbId = 42)
        val show = repository.getCachedShow(42)!!

        assertEquals(TvShowStatus.EN_DIFFUSION, show.status)
        assertEquals(mapOf(0 to 2, 1 to 10, 2 to 8), show.seasonEpisodeCounts)
        assertEquals(EpisodeKey(2, 4), show.lastAired?.key)
        assertEquals(AirDate.parse("2026-09-24"), show.lastAired?.airDate)
        assertEquals("Quatre", show.lastAired?.title)
        assertEquals(EpisodeKey(2, 5), show.nextToAir?.key)
        assertNull("un titre vide n'est pas conserve", show.nextToAir?.title)
        assertEquals(1, api.tvDetailsCalls)
    }

    @Test
    fun `un cache valide n'appelle plus TMDB`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        now += 23 * hour
        repository.refreshIfStale(42)

        assertEquals(1, api.tvDetailsCalls)
    }

    @Test
    fun `une serie en diffusion est rafraichie apres 24 heures`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        now += 24 * hour
        repository.refreshIfStale(42)

        assertEquals(2, api.tvDetailsCalls)
    }

    @Test
    fun `une serie terminee reste valide 30 jours`() = runTest {
        api.tvStatus = "Ended"
        api.nextEpisodeToAir = null
        val repository = repository()
        repository.refreshIfStale(42)

        now += 29 * day
        repository.refreshIfStale(42)
        assertEquals(1, api.tvDetailsCalls)

        now += 1 * day
        repository.refreshIfStale(42)
        assertEquals(2, api.tvDetailsCalls)
    }

    @Test
    fun `une horloge reculee rend le cache perime`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        now -= hour
        repository.refreshIfStale(42)

        assertEquals(2, api.tvDetailsCalls)
    }

    @Test
    fun `un rafraichissement met a jour la fiche en cache`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        api.tvStatus = "Ended"
        api.nextEpisodeToAir = null
        now += 2 * day
        repository.refreshIfStale(42)
        val show = repository.getCachedShow(42)!!

        assertEquals(TvShowStatus.TERMINEE, show.status)
        assertNull(show.nextToAir)
    }

    @Test
    fun `un echec reseau sans cache ne leve rien et ne stocke rien`() = runTest {
        api.shouldThrow = true
        val repository = repository()

        repository.refreshIfStale(42)

        assertNull(repository.getCachedShow(42))
    }

    @Test
    fun `un echec reseau conserve le cache perime`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        api.shouldThrow = true
        now += 3 * day
        repository.refreshIfStale(42)

        assertNotNull(repository.getCachedShow(42))
        assertEquals(TvShowStatus.EN_DIFFUSION, repository.getCachedShow(42)?.status)
    }

    @Test
    fun `le detail d'une saison est charge une fois puis reste valide`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        repository.refreshEpisodesIfStale(42, seasonNumber = 2)
        repository.refreshEpisodesIfStale(42, seasonNumber = 2)
        val show = repository.getCachedShow(42)!!

        assertEquals(1, api.tvSeasonCalls)
        assertEquals("Cinq", show.episodes[EpisodeKey(2, 5)]?.title)
        assertEquals(AirDate.parse("2026-10-08"), show.episodes[EpisodeKey(2, 5)]?.airDate)
        assertNull("un titre vide n'est pas conserve", show.episodes[EpisodeKey(2, 6)]?.title)
        assertNull(show.episodes[EpisodeKey(2, 6)]?.airDate)
    }

    @Test
    fun `le detail d'une saison perime est recharge`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)
        repository.refreshEpisodesIfStale(42, seasonNumber = 2)

        now += 2 * day
        repository.refreshEpisodesIfStale(42, seasonNumber = 2)

        assertEquals(2, api.tvSeasonCalls)
    }

    @Test
    fun `rafraichir la fiche conserve le detail deja charge des saisons`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)
        repository.refreshEpisodesIfStale(42, seasonNumber = 2)

        now += 2 * day
        repository.refreshIfStale(42)
        repository.refreshEpisodesIfStale(42, seasonNumber = 2)
        val show = repository.getCachedShow(42)!!

        assertEquals("Cinq", show.episodes[EpisodeKey(2, 5)]?.title)
        assertEquals("le detail date de plus de 24h : il est recharge une fois", 2, api.tvSeasonCalls)
    }

    @Test
    fun `le detail d'une saison n'est pas charge tant que la serie n'est pas en cache`() = runTest {
        val repository = repository()

        repository.refreshEpisodesIfStale(42, seasonNumber = 2)

        assertEquals(0, api.tvSeasonCalls)
    }

    @Test
    fun `le detail d'une saison inconnue de la serie n'est pas charge`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)

        repository.refreshEpisodesIfStale(42, seasonNumber = 9)

        assertEquals(0, api.tvSeasonCalls)
    }

    @Test
    fun `observeShows expose les series en cache indexees par tmdbId`() = runTest {
        val repository = repository()
        repository.refreshIfStale(42)
        repository.refreshIfStale(43)

        val shows = repository.observeShows().first()

        assertEquals(setOf(42L, 43L), shows.keys)
        assertTrue(shows.getValue(42).seasonEpisodeCounts.isNotEmpty())
    }

    @Test
    fun `la deconnexion vide le cache`() = runTest {
        val auth = FakeAuthRepository()
        val repository = repository(auth)
        repository.refreshIfStale(42)
        repository.refreshEpisodesIfStale(42, seasonNumber = 2)

        auth.signOut()

        assertNull(repository.getCachedShow(42))
        assertTrue(repository.observeShows().first().isEmpty())
    }
}
