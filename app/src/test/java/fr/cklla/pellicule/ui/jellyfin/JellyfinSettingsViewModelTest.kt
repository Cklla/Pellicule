package fr.cklla.pellicule.ui.jellyfin

import fr.cklla.pellicule.data.repository.FakeJellyfinRepository
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.domain.model.JellyfinPushHistoryResult
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class JellyfinSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeJellyfinRepository,
        mediaRepository: MediaRepository = fakeMediaRepository(FakeMediaDao()),
    ) = JellyfinSettingsViewModel(repository, mediaRepository)

    @Test
    fun `une connexion reussie vide le mot de passe et affiche le statut connecte`() = runTest {
        val repository = FakeJellyfinRepository()
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("https://jellyfin.exemple.fr")
        viewModel.onUsernameChanged("stef")
        viewModel.onPasswordChanged("secret")
        viewModel.onConnectClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("stef", state.connectedUsername)
        assertEquals("https://jellyfin.exemple.fr", state.connectedServerUrl)
        assertEquals("", state.password)
        assertNull(state.errorMessage)
        collectorJob.cancel()
    }

    @Test
    fun `une connexion echouee affiche l'erreur sans changer le statut`() = runTest {
        val repository = FakeJellyfinRepository().apply {
            connectResult = Resource.Error("Connexion au serveur Jellyfin impossible. Vérifie l'URL et les identifiants.")
        }
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("https://jellyfin.exemple.fr")
        viewModel.onUsernameChanged("stef")
        viewModel.onPasswordChanged("mauvais-mdp")
        viewModel.onConnectClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.connectedUsername)
        assertTrue(state.errorMessage != null)
        collectorJob.cancel()
    }

    @Test
    fun `une adresse en http signale une connexion non chiffree`() = runTest {
        val viewModel = viewModel(FakeJellyfinRepository())
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("http://192.168.1.10:8096")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCleartextServerUrl)

        viewModel.onServerUrlChanged("https://jellyfin.exemple.fr")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCleartextServerUrl)

        collectorJob.cancel()
    }

    @Test
    fun `se deconnecter reinitialise le formulaire et le statut`() = runTest {
        val repository = FakeJellyfinRepository()
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("https://jellyfin.exemple.fr")
        viewModel.onUsernameChanged("stef")
        viewModel.onConnectClicked()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDisconnectClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.connectedUsername)
        assertEquals("", state.serverUrl)
        collectorJob.cancel()
    }

    @Test
    fun `renvoyer l'historique appelle le repository et affiche le resultat`() = runTest {
        val repository = FakeJellyfinRepository().apply {
            setSession(JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"))
            pushWatchedHistoryResult = JellyfinPushHistoryResult(moviesMarkedPlayed = 2, episodesMarkedPlayed = 5)
        }
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPushHistoryClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPushingHistory)
        assertEquals(JellyfinPushHistoryResult(2, 5), state.pushHistoryResult)
        assertEquals(1, repository.pushedHistoryItems.size)
        collectorJob.cancel()
    }
}
