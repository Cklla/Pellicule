package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.data.repository.EpisodeRepositoryImpl
import fr.cklla.pellicule.data.repository.FakeEpisodeDao
import fr.cklla.pellicule.data.repository.FakeJellyfinRepository
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.FakeTvShowInfoRepository
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.NextEpisode
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.usecase.SetEpisodeWatchedUseCase
import fr.cklla.pellicule.domain.util.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BibliothequeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
    private val tvShowRepository = FakeTvShowInfoRepository()
    private val jellyfinRepository = FakeJellyfinRepository()

    // 2026-09-30 : date fixe pour que « déjà diffusé » ne dépende pas du jour où les tests tournent.
    private val timeSource = TimeSource { 1_790_000_000_000L }

    private fun viewModelFor(repository: MediaRepository) = BibliothequeViewModel(
        mediaRepository = repository,
        episodeRepository = episodeRepository,
        tvShowInfoRepository = tvShowRepository,
        setEpisodeWatched = SetEpisodeWatchedUseCase(repository, episodeRepository, tvShowRepository, jellyfinRepository, timeSource),
        timeSource = timeSource,
    )

    private fun show(
        tmdbId: Long = 42,
        status: TvShowStatus = TvShowStatus.TERMINEE,
        counts: Map<Int, Int> = mapOf(1 to 2, 2 to 2),
        nextToAir: EpisodeAirInfo? = null,
    ) = TvShowInfo(
        tmdbId,
        status,
        counts,
        // Tout ce qui précède cet épisode est déjà sorti : seuls ceux annoncés après sont à venir.
        lastAired = EpisodeAirInfo(EpisodeKey(99, 99), AirDate.parse("2026-09-01"), title = null),
        nextToAir = nextToAir,
    )

    private suspend fun MediaRepository.addSeries(
        status: WatchStatus = WatchStatus.EN_COURS,
        type: MediaType = MediaType.SERIE,
        tmdbId: Long? = 42,
    ): String = (addMedia(Media(title = "Severance", type = type, status = status, tmdbId = tmdbId)) as Resource.Success).data

    @Test
    fun `selectionner un filtre restreint la liste visible sans affecter les compteurs`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))
        repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))

        val viewModel = viewModelFor(repository)
        // uiState est un StateFlow "WhileSubscribed" : il ne collecte le repository
        // qu'une fois observé, comme le ferait la Composable via collectAsStateWithLifecycle.
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(BibliothequeFilter.A_VOIR)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.visibleMedia.size)
        assertEquals("Dune", state.visibleMedia.first().title)
        assertEquals(2, state.filterCounts[BibliothequeFilter.TOUS])
        assertEquals(BibliothequeFilter.A_VOIR, state.selectedFilter)

        collectorJob.cancel()
    }

    @Test
    fun `filtrer Vu par annee ne montre que les contenus visionnes cette annee-la`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))
        repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFilterSelected(BibliothequeFilter.VU)
        dispatcher.scheduler.advanceUntilIdle()

        val currentYear = watchedYear(viewModel.uiState.value.visibleMedia.first())
        viewModel.onWatchedYearSelected(currentYear)
        dispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.visibleMedia.size)
        assertEquals("Perfect Blue", state.visibleMedia.first().title)
        assertEquals(currentYear, state.selectedWatchedYear)

        // Re-cliquer sur la même année désélectionne (retour à "Toutes les années").
        viewModel.onWatchedYearSelected(currentYear)
        dispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(null, state.selectedWatchedYear)
        assertEquals(1, state.visibleMedia.size)

        collectorJob.cancel()
    }

    @Test
    fun `changer de filtre de statut reinitialise l'annee selectionnee`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFilterSelected(BibliothequeFilter.VU)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchedYearSelected(watchedYear(viewModel.uiState.value.visibleMedia.first()))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(BibliothequeFilter.TOUS)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedWatchedYear)

        collectorJob.cancel()
    }

    @Test
    fun `selectionner un type restreint la liste visible sans affecter les compteurs de statut`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))
        repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onTypeSelected(MediaType.FILM)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.visibleMedia.size)
        assertEquals("Dune", state.visibleMedia.first().title)
        assertEquals(2, state.filterCounts[BibliothequeFilter.TOUS])
        assertEquals(MediaType.FILM, state.selectedType)

        collectorJob.cancel()
    }

    @Test
    fun `re-selectionner le meme type le desactive`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onTypeSelected(MediaType.ANIME)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MediaType.ANIME, viewModel.uiState.value.selectedType)

        viewModel.onTypeSelected(MediaType.ANIME)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedType)

        collectorJob.cancel()
    }

    @Test
    fun `le filtre par type reste actif quand on change d'onglet de statut`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))
        repository.addMedia(Media(title = "One Piece", type = MediaType.ANIME, status = WatchStatus.A_VOIR))
        repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onTypeSelected(MediaType.ANIME)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onFilterSelected(BibliothequeFilter.A_VOIR)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MediaType.ANIME, state.selectedType)
        assertEquals(1, state.visibleMedia.size)
        assertEquals("One Piece", state.visibleMedia.first().title)

        collectorJob.cancel()
    }
    @Test
    fun `une serie En cours expose son prochain episode`() = runTest {
        tvShowRepository.put(show())
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()
        episodeRepository.setEpisodeWatched(id, EpisodeKey(1, 1), watched = true)

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NextEpisode.Available(EpisodeKey(1, 2), title = null), viewModel.uiState.value.nextEpisodes[id])
        collectorJob.cancel()
    }

    @Test
    fun `seules les series et anime En cours ont un prochain episode`() = runTest {
        tvShowRepository.put(show())
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addSeries(status = WatchStatus.A_VOIR)
        repository.addSeries(status = WatchStatus.VU)
        repository.addSeries(type = MediaType.FILM)
        repository.addSeries(tmdbId = null)
        val anime = repository.addSeries(type = MediaType.ANIME)

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(anime), viewModel.uiState.value.nextEpisodes.keys)
        collectorJob.cancel()
    }

    @Test
    fun `sans metadonnees en cache le prochain episode est inconnu et le plus un ne fait rien`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NextEpisode.Unknown, viewModel.uiState.value.nextEpisodes[id])
        assertTrue(episodeRepository.observeWatchedEpisodes(id).first().isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `plusieurs plus un enchaines avancent d'un episode chacun et franchissent la fin de saison`() = runTest {
        tvShowRepository.put(show(status = TvShowStatus.EN_DIFFUSION, counts = mapOf(1 to 2, 2 to 2)))
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val seen = mutableListOf<NextEpisode?>()
        repeat(3) {
            seen += viewModel.uiState.value.nextEpisodes[id]
            viewModel.onWatchNextEpisode(id)
            dispatcher.scheduler.advanceUntilIdle()
        }
        seen += viewModel.uiState.value.nextEpisodes[id]

        assertEquals(
            listOf(
                NextEpisode.Available(EpisodeKey(1, 1), null),
                NextEpisode.Available(EpisodeKey(1, 2), null),
                NextEpisode.Available(EpisodeKey(2, 1), null),
                NextEpisode.Available(EpisodeKey(2, 2), null),
            ),
            seen,
        )
        assertEquals(setOf(EpisodeKey(1, 1), EpisodeKey(1, 2), EpisodeKey(2, 1)), episodeRepository.observeWatchedEpisodes(id).first())
        collectorJob.cancel()
    }

    @Test
    fun `des plus un lances coup sur coup sans attendre ne marquent jamais deux fois le meme episode`() = runTest {
        tvShowRepository.put(show(status = TvShowStatus.EN_DIFFUSION, counts = mapOf(1 to 5)))
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onWatchNextEpisode(id)
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()

        val watched = episodeRepository.observeWatchedEpisodes(id).first()
        assertTrue("le second tap simultane est ignore, le suivant avance", watched == setOf(EpisodeKey(1, 1), EpisodeKey(1, 2)))
        collectorJob.cancel()
    }

    @Test
    fun `le plus un sur le dernier episode d'une serie terminee la passe a Vu`() = runTest {
        tvShowRepository.put(show(status = TvShowStatus.TERMINEE, counts = mapOf(1 to 2)))
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()
        episodeRepository.setEpisodeWatched(id, EpisodeKey(1, 1), watched = true)

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()

        val stored = repository.observeMediaById(id).first()
        assertEquals(WatchStatus.VU, stored?.status)
        assertNotNull(stored?.watchedAt)
        assertNull("une serie Vu n'a plus de carte prochain episode", viewModel.uiState.value.nextEpisodes[id])
        collectorJob.cancel()
    }

    @Test
    fun `un episode pas encore diffuse n'est pas propose et le plus un ne le marque pas`() = runTest {
        tvShowRepository.put(
            show(
                status = TvShowStatus.EN_DIFFUSION,
                counts = mapOf(1 to 3),
                nextToAir = EpisodeAirInfo(EpisodeKey(1, 3), AirDate.parse("2026-12-24"), "Trois"),
            ),
        )
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()
        episodeRepository.setEpisodeWatched(id, EpisodeKey(1, 1), watched = true)
        episodeRepository.setEpisodeWatched(id, EpisodeKey(1, 2), watched = true)

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            NextEpisode.Upcoming(EpisodeKey(1, 3), "Trois", AirDate.parse("2026-12-24")),
            viewModel.uiState.value.nextEpisodes[id],
        )
        assertEquals(2, episodeRepository.observeWatchedEpisodes(id).first().size)
        collectorJob.cancel()
    }

    @Test
    fun `sans reseau ni session Jellyfin le plus un met a jour la base locale`() = runTest {
        tvShowRepository.put(show(status = TvShowStatus.EN_DIFFUSION))
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(EpisodeKey(1, 1)), episodeRepository.observeWatchedEpisodes(id).first())
        assertTrue(jellyfinRepository.pushedEpisodes.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `avec une session Jellyfin chaque plus un est pousse`() = runTest {
        tvShowRepository.put(show(status = TvShowStatus.EN_DIFFUSION))
        jellyfinRepository.setSession(JellyfinSession("https://jellyfin.exemple.fr", "user-1", "stef", "token"))
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()

        val viewModel = viewModelFor(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchNextEpisode(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(EpisodeKey(1, 1), EpisodeKey(1, 2)),
            jellyfinRepository.pushedEpisodes.map { it.second },
        )
        collectorJob.cancel()
    }

    @Test
    fun `le cache n'est rafraichi que pour les series En cours avec un tmdbId`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addSeries(tmdbId = 42)
        repository.addSeries(status = WatchStatus.A_VOIR, tmdbId = 43)
        repository.addSeries(type = MediaType.FILM, tmdbId = 44)
        repository.addSeries(tmdbId = null)

        viewModelFor(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(42L), tvShowRepository.refreshedShows)
    }

    @Test
    fun `le detail de la saison n'est demande que si le titre manque`() = runTest {
        tvShowRepository.put(show(counts = mapOf(1 to 2, 2 to 2)))
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()
        episodeRepository.setEpisodeWatched(id, EpisodeKey(1, 2), watched = true)

        viewModelFor(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(42L to 2), tvShowRepository.refreshedSeasons)
    }

    @Test
    fun `aucun detail de saison n'est demande quand le titre est deja connu`() = runTest {
        tvShowRepository.put(
            show(
                status = TvShowStatus.EN_DIFFUSION,
                counts = mapOf(1 to 3),
                nextToAir = EpisodeAirInfo(EpisodeKey(1, 3), AirDate.parse("2026-12-24"), "Trois"),
            ),
        )
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = repository.addSeries()
        episodeRepository.setEpisodeWatched(id, EpisodeKey(1, 2), watched = true)

        viewModelFor(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(tvShowRepository.refreshedSeasons.isEmpty())
    }
}
