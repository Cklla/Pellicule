package fr.cklla.pellicule.ui.stats

import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeMediaDao()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seed() {
        listOf(
            Media(id = "1", title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, watchedAt = watchedOn(2026)),
            Media(id = "2", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = watchedOn(2026)),
            Media(id = "3", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU, watchedAt = watchedOn(2025)),
        ).forEach { dao.insert(it.toEntity()) }
    }

    @Test
    fun `par defaut toutes annees et tous types, annees disponibles de la plus recente a la plus ancienne`() = runTest(dispatcher) {
        seed()
        val viewModel = StatsViewModel(fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.stats.watchedCount)
        assertNull(state.stats.selectedYear)
        assertNull(state.stats.selectedType)
        assertEquals(listOf(2026, 2025), state.availableYears)
    }

    @Test
    fun `selectionner une annee filtre, la reselectionner revient a toutes les annees`() = runTest(dispatcher) {
        seed()
        val viewModel = StatsViewModel(fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onYearSelected(2026)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.stats.watchedCount)

        viewModel.onYearSelected(2026)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.stats.selectedYear)
        assertEquals(3, viewModel.uiState.value.stats.watchedCount)
    }

    @Test
    fun `le type se bascule au reclic et se combine a l'annee`() = runTest(dispatcher) {
        seed()
        val viewModel = StatsViewModel(fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onYearSelected(2026)
        viewModel.onTypeSelected(MediaType.SERIE)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.stats.watchedCount)
        assertEquals(MediaType.SERIE, viewModel.uiState.value.stats.selectedType)

        viewModel.onTypeSelected(MediaType.SERIE)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.stats.selectedType)
        assertEquals(2, viewModel.uiState.value.stats.watchedCount)
    }

    @Test
    fun `une annee qui n'a plus de visionnage n'est plus un filtre actif`() = runTest(dispatcher) {
        seed()
        val viewModel = StatsViewModel(fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onYearSelected(2025)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.stats.watchedCount)

        dao.insert(Media(id = "3", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.EN_COURS).toEntity())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.stats.selectedYear)
        assertEquals(listOf(2026), viewModel.uiState.value.availableYears)
        assertEquals(2, viewModel.uiState.value.stats.watchedCount)
    }
}
