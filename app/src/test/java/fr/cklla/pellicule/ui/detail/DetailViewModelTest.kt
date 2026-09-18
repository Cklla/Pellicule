package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.pellicule.data.repository.EpisodeRepositoryImpl
import fr.cklla.pellicule.data.repository.FakeEpisodeDao
import fr.cklla.pellicule.data.repository.FakeJellyfinRepository
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelFor(
        mediaId: String,
        mediaRepository: MediaRepository,
        tvDetailsRepository: FakeTvDetailsRepository = FakeTvDetailsRepository(),
        episodeRepository: EpisodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao()),
        jellyfinRepository: JellyfinRepository = FakeJellyfinRepository(),
        synopsisRepository: FakeSynopsisRepository = FakeSynopsisRepository(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PelliculeDestinations.DETAIL_ARG_MEDIA_ID to mediaId)),
        mediaRepository = mediaRepository,
        tvDetailsRepository = tvDetailsRepository,
        episodeRepository = episodeRepository,
        jellyfinRepository = jellyfinRepository,
        synopsisRepository = synopsisRepository,
    )

    private fun previewViewModelFor(
        mediaRepository: MediaRepository,
        tmdbId: Long = 42,
        title: String = "Severance",
        type: String = "SERIE",
        year: String = "2022",
        posterUrl: String = "",
        tvDetailsRepository: FakeTvDetailsRepository = FakeTvDetailsRepository(),
        episodeRepository: EpisodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao()),
        jellyfinRepository: JellyfinRepository = FakeJellyfinRepository(),
        synopsisRepository: FakeSynopsisRepository = FakeSynopsisRepository(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                PelliculeDestinations.DETAIL_APERCU_ARG_TMDB_ID to tmdbId,
                PelliculeDestinations.DETAIL_APERCU_ARG_TITLE to title,
                PelliculeDestinations.DETAIL_APERCU_ARG_TYPE to type,
                PelliculeDestinations.DETAIL_APERCU_ARG_YEAR to year,
                PelliculeDestinations.DETAIL_APERCU_ARG_POSTER_URL to posterUrl,
            ),
        ),
        mediaRepository = mediaRepository,
        tvDetailsRepository = tvDetailsRepository,
        episodeRepository = episodeRepository,
        jellyfinRepository = jellyfinRepository,
        synopsisRepository = synopsisRepository,
    )

    @Test
    fun `changer le statut persiste la mise a jour`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(mediaId, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.VU)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WatchStatus.VU, viewModel.uiState.value.media?.status)
        collectorJob.cancel()
    }

    @Test
    fun `retirer le contenu vide la fiche`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(mediaId, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRemoveMedia()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.media)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir une fiche suivie charge son synopsis`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1))
        val mediaId = (addResult as Resource.Success).data
        val synopsisRepository = FakeSynopsisRepository().apply { response = Resource.Success("Paul Atréides...") }

        val viewModel = viewModelFor(mediaId, repository, synopsisRepository = synopsisRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Paul Atréides...", viewModel.uiState.value.synopsis)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir un apercu charge aussi son synopsis`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val synopsisRepository = FakeSynopsisRepository().apply { response = Resource.Success("Un employé découpe son esprit...") }
        val viewModel = previewViewModelFor(repository, tmdbId = 42, synopsisRepository = synopsisRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Un employé découpe son esprit...", viewModel.uiState.value.synopsis)
        collectorJob.cancel()
    }

    @Test
    fun `un FILM n'a pas de section episodes`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1),
        )
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(mediaId, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.seasons.isEmpty())
        assertTrue(viewModel.uiState.value.episodes.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir une serie charge les saisons et selectionne la premiere saison reguliere`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(
                listOf(
                    Season(seasonNumber = 0, name = "Spéciaux", episodeCount = 1, posterUrl = null),
                    Season(seasonNumber = 1, name = "Saison 1", episodeCount = 9, posterUrl = null),
                ),
            )
            episodesResponseBySeason = mapOf(
                1 to Resource.Success(listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null))),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.seasons.size)
        assertEquals(1, state.selectedSeasonNumber)
        assertEquals(1, state.episodes.size)
        assertEquals("Bon travail", state.episodes.first().title)
        assertEquals(false, state.episodes.first().watched)
        collectorJob.cancel()
    }

    @Test
    fun `marquer un episode vu met a jour son statut dans l'etat`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.episodes.first().watched)
        collectorJob.cancel()
    }

    @Test
    fun `changer de saison recharge les episodes de la saison choisie`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(
                listOf(
                    Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null),
                    Season(seasonNumber = 2, name = "Saison 2", episodeCount = 1, posterUrl = null),
                ),
            )
            episodesResponseBySeason = mapOf(
                1 to Resource.Success(listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "S1E1", stillUrl = null))),
                2 to Resource.Success(listOf(EpisodeInfo(seasonNumber = 2, episodeNumber = 1, title = "S2E1", stillUrl = null))),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSeasonSelected(2)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.selectedSeasonNumber)
        assertEquals("S2E1", state.episodes.first().title)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir un apercu depuis la recherche n'est pas dans le suivi`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val viewModel = previewViewModelFor(repository, tmdbId = 42, title = "Severance", year = "2022")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInBacklog)
        assertEquals("Severance", state.media?.title)
        assertEquals(2022, state.media?.releaseYear)
        assertEquals("", state.media?.id)
        collectorJob.cancel()
    }

    @Test
    fun `un apercu sans tmdbId valide n'a pas de contenu`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val viewModel = DetailViewModel(
            savedStateHandle = SavedStateHandle(emptyMap()),
            mediaRepository = repository,
            tvDetailsRepository = FakeTvDetailsRepository(),
            episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao()),
            jellyfinRepository = FakeJellyfinRepository(),
            synopsisRepository = FakeSynopsisRepository(),
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.media)
        collectorJob.cancel()
    }

    @Test
    fun `ajouter depuis l'apercu persiste le contenu et bascule dans le suivi`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val viewModel = previewViewModelFor(repository, tmdbId = 42)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddMedia()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isInBacklog)
        assertTrue(state.media?.id?.isNotEmpty() == true)
        assertEquals(1, repository.observeMedia().first().size)
        collectorJob.cancel()
    }

    @Test
    fun `ajouter une serie depuis l'apercu charge la section episodes`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }
        val viewModel = previewViewModelFor(repository, tmdbId = 95396, type = "SERIE", tvDetailsRepository = tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddMedia()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.seasons.size)
        assertEquals(1, state.episodes.size)
        assertEquals("Bon travail", state.episodes.first().title)
        collectorJob.cancel()
    }

    @Test
    fun `marquer un episode vu pousse vers Jellyfin quand une session est active`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }
        val jellyfinRepository = FakeJellyfinRepository().apply {
            setSession(JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"))
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, jellyfinRepository.pushedEpisodes.size)
        val (media, episode, watched) = jellyfinRepository.pushedEpisodes.first()
        assertEquals(mediaId, media.id)
        assertEquals(1, episode.seasonNumber)
        assertEquals(1, episode.episodeNumber)
        assertTrue(watched)
        collectorJob.cancel()
    }

    @Test
    fun `marquer un episode vu ne pousse rien sans session Jellyfin`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }
        val jellyfinRepository = FakeJellyfinRepository()

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(jellyfinRepository.pushedEpisodes.isEmpty())
        assertTrue(viewModel.uiState.value.episodes.first().watched)
        collectorJob.cancel()
    }
}
