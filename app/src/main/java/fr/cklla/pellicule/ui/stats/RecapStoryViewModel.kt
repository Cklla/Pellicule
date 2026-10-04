package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.recap.RecapSlide
import fr.cklla.pellicule.domain.recap.buildRecapSlides
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Slides à afficher. [isLoading] vaut `true` tant que la Bibliothèque n'a pas répondu : l'écran
 * attend la première liste réelle avant de créer son pager, pour qu'un état de page restauré ne
 * soit pas ramené à 0 par une liste de slides encore vide.
 */
data class RecapStoryUiState(
    val isLoading: Boolean = true,
    val slides: List<RecapSlide> = emptyList(),
)

/**
 * Récap en images d'une année. L'année vient de la route ; les slides sont recalculées à chaque
 * changement de la Bibliothèque (note, statut), jamais figées à l'ouverture.
 */
@HiltViewModel
class RecapStoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mediaRepository: MediaRepository,
    timeSource: TimeSource,
) : ViewModel() {

    val year: Int = checkNotNull(savedStateHandle[PelliculeDestinations.RECAP_ARG_YEAR])

    val uiState: StateFlow<RecapStoryUiState> = mediaRepository.observeMedia()
        .map { media -> RecapStoryUiState(isLoading = false, slides = buildRecapSlides(media, year, timeSource.zone)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecapStoryUiState(),
        )
}
