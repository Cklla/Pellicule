package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.SavedStateHandle
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.recap.RecapSlide
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecapStoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeMediaDao()
    private val timeSource = TimeSource { 0L }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(year: Int = 2026) = RecapStoryViewModel(
        SavedStateHandle(mapOf(PelliculeDestinations.RECAP_ARG_YEAR to year)),
        fakeMediaRepository(dao),
        timeSource,
    )

    private fun film(id: String, rating: Int? = null, status: WatchStatus = WatchStatus.VU, year: Int = 2026) = Media(
        id = id,
        title = id,
        type = MediaType.FILM,
        status = status,
        rating = rating,
        watchedAt = if (status == WatchStatus.VU) watchedOn(year) else null,
    )

    private fun RecapStoryViewModel.keys() = uiState.value.slides.map { it.key }

    @Test
    fun `l'annee de la route est lue depuis le SavedStateHandle`() = runTest(dispatcher) {
        dao.insert(film("de-2026", year = 2026).toEntity())
        dao.insert(film("de-2025", year = 2025).toEntity())
        val viewModel = viewModel(year = 2025)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2025, viewModel.year)
        val mosaic = viewModel.uiState.value.slides.filterIsInstance<RecapSlide.Mosaic>().single()
        assertEquals(listOf("de-2025"), mosaic.media.map { it.id })
    }

    @Test
    fun `l'etat est en chargement puis sans slide quand rien n'a ete vu`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.slides.isEmpty())
    }

    @Test
    fun `une note modifiee met les slides a jour en direct`() = runTest(dispatcher) {
        dao.insert(film("dune", rating = 3).toEntity())
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse("type-FILM" in viewModel.keys())

        dao.insert(film("dune", rating = 5).toEntity())
        advanceUntilIdle()

        val slide = viewModel.uiState.value.slides.filterIsInstance<RecapSlide.TypeFavorites>().single()
        assertEquals(MediaType.FILM, slide.type)
        assertEquals(listOf("dune"), slide.favorites.map { it.id })
    }

    @Test
    fun `un changement de statut met le total et la mosaique a jour en direct`() = runTest(dispatcher) {
        dao.insert(film("dune").toEntity())
        dao.insert(film("akira", status = WatchStatus.EN_COURS).toEntity())
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value.slides.first() as RecapSlide.Total).total)

        dao.insert(film("akira").toEntity())
        advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value.slides.first() as RecapSlide.Total).total)
        assertEquals(2, viewModel.uiState.value.slides.filterIsInstance<RecapSlide.Mosaic>().single().media.size)

        dao.insert(film("dune", status = WatchStatus.A_VOIR).toEntity())
        dao.insert(film("akira", status = WatchStatus.A_VOIR).toEntity())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.slides.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
