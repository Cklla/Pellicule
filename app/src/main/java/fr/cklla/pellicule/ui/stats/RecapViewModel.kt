package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Année reçue par la route de navigation. Les chiffres sont calculés en direct sur la
 * Bibliothèque courante par [computeStats], jamais figés au moment de l'ouverture.
 */
@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mediaRepository: MediaRepository,
) : ViewModel() {

    val year: Int = checkNotNull(savedStateHandle[PelliculeDestinations.RECAP_ARG_YEAR])

    val uiState: StateFlow<StatsData> = mediaRepository.observeMedia()
        .map { media -> computeStats(media, selectedYear = year) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsData(selectedYear = year),
        )
}
