package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.MediaRepositoryImpl
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
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

    private fun viewModelFor(repository: MediaRepositoryImpl, mediaId: String) =
        DetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(PelliculeDestinations.DETAIL_ARG_MEDIA_ID to mediaId)),
            mediaRepository = repository,
        )

    @Test
    fun `changer le statut persiste la mise a jour`() = runTest {
        val repository = MediaRepositoryImpl(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(repository, mediaId)
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

        val viewModel = viewModelFor(repository, mediaId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRemoveMedia()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.media)
        collectorJob.cancel()
    }
}
