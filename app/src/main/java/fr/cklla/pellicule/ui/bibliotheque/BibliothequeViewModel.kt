package fr.cklla.pellicule.ui.bibliotheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BibliothequeViewModel @Inject constructor(
    mediaRepository: MediaRepository,
) : ViewModel() {

    val uiState: StateFlow<BibliothequeUiState> = mediaRepository.observeMedia()
        .map { media -> BibliothequeUiState(media = media) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BibliothequeUiState(),
        )
}
