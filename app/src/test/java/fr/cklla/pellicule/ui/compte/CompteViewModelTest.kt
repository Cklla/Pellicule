package fr.cklla.pellicule.ui.compte

import fr.cklla.pellicule.data.repository.FakeAuthRepository
import fr.cklla.pellicule.data.repository.FakeJellyfinRepository
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.ui.stats.watchedOn
import java.time.LocalDate
import java.time.ZoneId
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
class CompteViewModelTest {

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

    private fun viewModelOn(date: LocalDate) = CompteViewModel(
        authRepository = FakeAuthRepository(),
        jellyfinRepository = FakeJellyfinRepository(),
        mediaRepository = fakeMediaRepository(dao),
        timeSource = TimeSource { date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() },
    )

    private suspend fun seed() {
        dao.insert(Media(id = "1", title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, watchedAt = watchedOn(2026)).toEntity())
        dao.insert(Media(id = "2", title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = watchedOn(2025)).toEntity())
    }

    @Test
    fun `hors fenetre, aucune carte de recap`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModelOn(LocalDate.of(2026, 10, 2))
        backgroundScope.launch { viewModel.recap.collect {} }
        advanceUntilIdle()

        assertNull(viewModel.recap.value)
    }

    @Test
    fun `en decembre, la carte porte sur l'annee en cours`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModelOn(LocalDate.of(2026, 12, 27))
        backgroundScope.launch { viewModel.recap.collect {} }
        advanceUntilIdle()

        assertEquals(RecapCardState(year = 2026, watchedCount = 1), viewModel.recap.value)
    }

    @Test
    fun `en janvier, la carte porte sur l'annee precedente`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModelOn(LocalDate.of(2026, 1, 15))
        backgroundScope.launch { viewModel.recap.collect {} }
        advanceUntilIdle()

        assertEquals(RecapCardState(year = 2025, watchedCount = 1), viewModel.recap.value)
    }
}
