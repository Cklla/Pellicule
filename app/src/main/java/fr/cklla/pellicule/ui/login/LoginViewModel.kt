package fr.cklla.pellicule.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Ne s'occupe que de déclencher la connexion : une fois réussie, `AuthRepository.currentUser`
 * (observé par `PelliculeApp`) bascule automatiquement l'app hors de cet écran — pas besoin de
 * naviguer explicitement d'ici.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onSignInClicked(context: Context) {
        if (_uiState.value.isLoading) return
        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            _uiState.value = when (val result = authRepository.signIn(context)) {
                is Resource.Success -> LoginUiState()
                is Resource.Error -> LoginUiState(errorMessage = result.message)
            }
        }
    }
}
