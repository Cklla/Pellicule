package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.pellicule.data.repository.EpisodeRepositoryImpl
import fr.cklla.pellicule.data.repository.FakeEpisodeDao
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.MediaRepositoryImpl
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        mediaRepository: MediaRepositoryImpl,
        tvDetailsRepository: FakeTvDetailsRepository = FakeTvDetailsRepository(),
        episodeRepository: EpisodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao()),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PelliculeDestinations.DETAIL_ARG_MEDIA_ID to mediaId)),
        mediaRepository = mediaRepository,
        tvDetailsRepository = tvDetailsRepository,
        episodeRepository = episodeRepository,
    )

    @Test
    fun `changer le statut persiste la mise a jour`() = runTest {
        val repository = MediaRepositoryImpl(FakeMediaDao())
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
        val repository = MediaRepositoryImpl(FakeMediaDao())
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
    fun `un FILM n'a pas de section episodes`() = runTest {
        val repository = MediaRepositoryImpl(FakeMediaDao())
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
        val repository = MediaRepositoryImpl(FakeMediaDao())
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
        val repository = MediaRepositoryImpl(FakeMediaDao())
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
        val repository = MediaRepositoryImpl(FakeMediaDao())
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
}
