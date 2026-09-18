package fr.cklla.pellicule.ui.recherche

import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.MediaRepositoryImpl
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RechercheViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleResult(tmdbId: Long = 1, title: String = "Dune") =
        MediaSearchResult(tmdbId = tmdbId, title = title, type = MediaType.FILM, year = 2021, posterUrl = null)

    @Test
    fun `changer la recherche declenche un appel apres le debounce et expose les resultats`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val searchRepository = FakeMediaSearchRepository().apply {
            response = Resource.Success(listOf(sampleResult()))
        }
        val viewModel = RechercheViewModel(mediaRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("dune")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertEquals("Dune", state.results.first().title)
        assertEquals(false, state.isSearching)
        assertEquals(1, searchRepository.searchCallCount)
        collectorJob.cancel()
    }

    @Test
    fun `une recherche vide ne declenche aucun appel reseau`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val searchRepository = FakeMediaSearchRepository()
        val viewModel = RechercheViewModel(mediaRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, searchRepository.searchCallCount)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `plusieurs frappes rapprochees ne declenchent qu'un seul appel reseau`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val searchRepository = FakeMediaSearchRepository().apply {
            response = Resource.Success(listOf(sampleResult()))
        }
        val viewModel = RechercheViewModel(mediaRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("d")
        viewModel.onQueryChanged("du")
        viewModel.onQueryChanged("dun")
        viewModel.onQueryChanged("dune")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, searchRepository.searchCallCount)
        collectorJob.cancel()
    }

    @Test
    fun `une erreur reseau remonte comme message d'erreur sans resultat`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val searchRepository = FakeMediaSearchRepository().apply {
            response = Resource.Error("Impossible de contacter TMDB.")
        }
        val viewModel = RechercheViewModel(mediaRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("dune")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Impossible de contacter TMDB.", state.errorMessage)
        assertTrue(state.results.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `ajouter un resultat au suivi le fait apparaitre comme deja ajoute`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val searchRepository = FakeMediaSearchRepository()
        val viewModel = RechercheViewModel(mediaRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddMedia(sampleResult(tmdbId = 42))
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.trackedMediaIdsByTmdbId[42L])
        collectorJob.cancel()
    }
}
