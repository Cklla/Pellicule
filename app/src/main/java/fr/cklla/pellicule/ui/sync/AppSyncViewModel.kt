package fr.cklla.pellicule.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Déclenche la synchro Jellyfin (statut vu des épisodes, voir `JellyfinRepository`) à chaque
 * ouverture/reprise de l'app — voir `PelliculeApp`, qui observe `Lifecycle.Event.ON_RESUME` au
 * niveau racine et appelle [onAppResumed]. Scope Activity (survit à la navigation entre onglets,
 * une seule synchro en vol à la fois même si plusieurs ON_RESUME arrivent coup sur coup).
 */
@HiltViewModel
class AppSyncViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    private val syncMutex = Mutex()

    fun onAppResumed() {
        if (jellyfinRepository.session.value == null) return
        viewModelScope.launch {
            if (syncMutex.isLocked) return@launch
            syncMutex.withLock {
                val items = mediaRepository.observeMedia().first()
                jellyfinRepository.syncTrackedSeries(items)
            }
        }
    }
}
