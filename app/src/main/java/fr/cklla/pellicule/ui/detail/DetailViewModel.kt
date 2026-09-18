package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le contenu affiché est chargé une fois depuis le repository, puis conservé dans une copie de
 * travail locale ([workingMedia]) que chaque action met à jour avant de persister (fire-and-forget)
 * via le repository — évite qu'un aller-retour Room en cours n'écrase un changement de statut fait
 * juste après.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle[PelliculeDestinations.DETAIL_ARG_MEDIA_ID])

    private val workingMedia = MutableStateFlow<Media?>(null)
    private val isLoading = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            workingMedia.value = mediaRepository.observeMediaById(mediaId).first()
            isLoading.value = false
        }
    }

    val uiState: StateFlow<DetailUiState> = combine(isLoading, workingMedia) { loading, media ->
        DetailUiState(isLoading = loading, media = media)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = isLoading.value, media = workingMedia.value),
    )

    fun onStatusSelected(status: WatchStatus) = applyEdit { it.copy(status = status) }

    fun onRemoveMedia() {
        // Retrait optimiste : l'écran revient en arrière dès que `media` devient `null`
        // (voir DetailScreen), sans attendre l'aller-retour Room.
        workingMedia.value = null
        viewModelScope.launch { mediaRepository.deleteMedia(mediaId) }
    }

    private fun applyEdit(transform: (Media) -> Media) {
        val updated = workingMedia.value?.let(transform) ?: return
        workingMedia.value = updated
        viewModelScope.launch { mediaRepository.updateMedia(updated) }
    }
}
