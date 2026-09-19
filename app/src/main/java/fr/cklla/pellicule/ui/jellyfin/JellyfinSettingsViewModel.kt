package fr.cklla.pellicule.ui.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Formulaire de connexion à un serveur Jellyfin : module entièrement optionnel, accessible depuis
 * Stats. [JellyfinRepository.session] pilote l'affichage statut connecté/déconnecté ; les champs
 * de saisie restent locaux à cet écran.
 */
@HiltViewModel
class JellyfinSettingsViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val formState = MutableStateFlow(JellyfinSettingsUiState())

    val uiState: StateFlow<JellyfinSettingsUiState> = combine(
        formState,
        jellyfinRepository.session,
    ) { form, session ->
        form.copy(
            connectedUsername = session?.username,
            connectedServerUrl = session?.serverUrl,
            isCleartextServerUrl = form.serverUrl.trim().startsWith("http://", ignoreCase = true),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JellyfinSettingsUiState(),
    )

    fun onServerUrlChanged(value: String) = updateForm { it.copy(serverUrl = value) }
    fun onUsernameChanged(value: String) = updateForm { it.copy(username = value) }
    fun onPasswordChanged(value: String) = updateForm { it.copy(password = value) }

    fun onConnectClicked() {
        val current = formState.value
        if (current.isConnecting || current.serverUrl.isBlank() || current.username.isBlank()) return
        updateForm { it.copy(isConnecting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = jellyfinRepository.connect(current.serverUrl, current.username, current.password)
            updateForm { form ->
                when (result) {
                    is Resource.Success -> form.copy(isConnecting = false, password = "")
                    is Resource.Error -> form.copy(isConnecting = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onDisconnectClicked() {
        jellyfinRepository.disconnect()
        formState.value = JellyfinSettingsUiState()
    }

    fun onPushHistoryClicked() {
        if (formState.value.isPushingHistory) return
        updateForm { it.copy(isPushingHistory = true, pushHistoryResult = null) }
        viewModelScope.launch {
            val items = mediaRepository.observeMedia().first()
            val result = jellyfinRepository.pushWatchedHistory(items)
            updateForm { it.copy(isPushingHistory = false, pushHistoryResult = result) }
        }
    }

    private inline fun updateForm(transform: (JellyfinSettingsUiState) -> JellyfinSettingsUiState) {
        formState.value = transform(formState.value)
    }
}
