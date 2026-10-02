package fr.cklla.pellicule.ui.compte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.recap.recapTargetYear
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.domain.util.today
import fr.cklla.pellicule.ui.stats.computeStats
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Contenu de la carte « Récap » de l'onglet Compte. */
data class RecapCardState(val year: Int, val watchedCount: Int)

/** Compte Google connecté (avec déconnexion) et statut de connexion Jellyfin, à cet endroit
 * plutôt que dans un écran Réglages dédié (seules actions de ce type de l'app). */
@HiltViewModel
class CompteViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    jellyfinRepository: JellyfinRepository,
    mediaRepository: MediaRepository,
    timeSource: TimeSource,
) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser

    val connectedServerUrl: StateFlow<String?> = jellyfinRepository.session
        .map { it?.serverUrl }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    // Année du récap pour la date du jour, calculée une fois à la création : `null` hors fenêtre,
    // alors aucune carte n'est affichée. Le nombre de contenus vus est suivi en direct.
    private val recapYear: Int? = recapTargetYear(timeSource.today())

    val recap: StateFlow<RecapCardState?> =
        if (recapYear == null) {
            MutableStateFlow(null)
        } else {
            mediaRepository.observeMedia()
                .map { media -> RecapCardState(recapYear, computeStats(media, selectedYear = recapYear).watchedCount) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = RecapCardState(recapYear, watchedCount = 0),
                )
        }

    fun onSignOutClicked() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
