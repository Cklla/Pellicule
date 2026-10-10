package fr.cklla.pellicule.ui.login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * N'expose que l'état de connexion, utilisé par `PelliculeApp` pour décider d'afficher
 * [LoginScreen], le `NavHost` normal, ou rien le temps que la session enregistrée se charge —
 * connexion obligatoire au lancement, pas de mode "suivi local sans compte".
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState
}
