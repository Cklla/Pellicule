package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun `selectionner un filtre restreint la liste visible sans affecter les compteurs`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        repository.addMedia(Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU))
        repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))

        val viewModel = BibliothequeViewModel(repository)
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
}
