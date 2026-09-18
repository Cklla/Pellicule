package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** N'expose pour l'instant que le statut de connexion Jellyfin, affiché en haut de Stats (voir `JellyfinSettingsScreen` pour le formulaire). */
@HiltViewModel
class StatsViewModel @Inject constructor(jellyfinRepository: JellyfinRepository) : ViewModel() {

    val connectedServerUrl: StateFlow<String?> = jellyfinRepository.session
        .map { it?.serverUrl }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)
}
