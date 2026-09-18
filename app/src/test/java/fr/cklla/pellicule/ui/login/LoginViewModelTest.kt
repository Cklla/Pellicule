package fr.cklla.pellicule.ui.login

import android.content.ContextWrapper
import fr.cklla.pellicule.data.repository.FakeAuthRepository
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// FakeAuthRepository ignore entièrement le Context reçu : un ContextWrapper vide suffit, pas
// besoin de Robolectric juste pour ce paramètre imposé par Credential Manager.
private val fakeContext = ContextWrapper(null)

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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
    fun `connexion reussie remet l'etat a zero (pas d'erreur, pas de chargement)`() = runTest {
        val authRepository = FakeAuthRepository(
            user = null,
            signInResult = Resource.Success(AuthUser(uid = "u1", displayName = "Ada")),
        )
        val viewModel = LoginViewModel(authRepository)

        viewModel.onSignInClicked(fakeContext)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `connexion echouee expose le message d'erreur`() = runTest {
        val authRepository = FakeAuthRepository(
            user = null,
            signInResult = Resource.Error("Connexion annulée."),
        )
        val viewModel = LoginViewModel(authRepository)

        viewModel.onSignInClicked(fakeContext)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("Connexion annulée.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `un second clic pendant le chargement est ignore`() = runTest {
        val authRepository = FakeAuthRepository(
            user = null,
            signInResult = Resource.Success(AuthUser(uid = "u1", displayName = "Ada")),
        )
        val viewModel = LoginViewModel(authRepository)

        viewModel.onSignInClicked(fakeContext)
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.onSignInClicked(fakeContext) // ignoré : une connexion est déjà en cours
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
    }
}
