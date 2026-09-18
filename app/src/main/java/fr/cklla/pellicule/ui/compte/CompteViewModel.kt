package fr.cklla.pellicule.ui.compte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Compte Google connecté (avec déconnexion) et statut de connexion Jellyfin, à cet endroit
 * plutôt que dans un écran Réglages dédié (seules actions de ce type de l'app). */
@HiltViewModel
class CompteViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser

    val connectedServerUrl: StateFlow<String?> = jellyfinRepository.session
        .map { it?.serverUrl }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onSignOutClicked() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
