package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.SavedStateHandle
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelsTest {

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
            Media(id = "film-26", title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, watchedAt = watchedOn(2026)),
            Media(id = "serie-26", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = watchedOn(2026)),
            Media(id = "serie-25", title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = watchedOn(2025)),
            Media(id = "a-voir", title = "Akira", type = MediaType.ANIME, status = WatchStatus.A_VOIR),
        ).forEach { dao.insert(it.toEntity()) }
    }

    private fun handle(year: Int, type: String? = null) = SavedStateHandle(
        buildMap {
            put(PelliculeDestinations.RECAP_ARG_YEAR, year)
            if (type != null) put(PelliculeDestinations.RECAP_ARG_TYPE, type)
        },
    )

    @Test
    fun `le recap calcule les stats de l'annee ciblee en direct`() = runTest(dispatcher) {
        seed()
        val viewModel = RecapViewModel(handle(2026), fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2026, viewModel.year)
        assertEquals(2, viewModel.uiState.value.watchedCount)
        assertEquals(mapOf(MediaType.FILM to 1, MediaType.SERIE to 1, MediaType.ANIME to 0), viewModel.uiState.value.countsByType)

        dao.insert(Media(id = "anime-26", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU, watchedAt = watchedOn(2026)).toEntity())
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.watchedCount)
    }

    @Test
    fun `la liste du recap sans type contient tous les contenus vus de l'annee`() = runTest(dispatcher) {
        seed()
        val viewModel = RecapMediaViewModel(handle(2026, PelliculeDestinations.RECAP_MEDIA_TYPE_ALL), fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.media.collect {} }
        advanceUntilIdle()

        assertNull(viewModel.type)
        assertEquals(setOf("film-26", "serie-26"), viewModel.media.value.map { it.id }.toSet())
    }

    @Test
    fun `la liste du recap par type ne contient que ce type pour l'annee`() = runTest(dispatcher) {
        seed()
        val viewModel = RecapMediaViewModel(handle(2026, MediaType.SERIE.name), fakeMediaRepository(dao))
        backgroundScope.launch { viewModel.media.collect {} }
        advanceUntilIdle()

        assertEquals(MediaType.SERIE, viewModel.type)
        assertEquals(listOf("serie-26"), viewModel.media.value.map { it.id })
    }

    @Test
    fun `parseRecapType ignore toute valeur inconnue`() {
        assertEquals(MediaType.ANIME, parseRecapType("ANIME"))
        assertNull(parseRecapType("TOUS"))
        assertNull(parseRecapType("n'importe quoi"))
        assertNull(parseRecapType(null))
    }
}
