package fr.cklla.pellicule.ui.bibliotheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BibliothequeViewModel @Inject constructor(
    mediaRepository: MediaRepository,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(BibliothequeFilter.TOUS)

    val uiState: StateFlow<BibliothequeUiState> = combine(
        mediaRepository.observeMedia(),
        selectedFilter,
    ) { media, filter ->
        BibliothequeUiState(
            isLoading = false,
            visibleMedia = filterMedia(media, filter),
            selectedFilter = filter,
            filterCounts = countByFilter(media),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliothequeUiState(),
    )

    fun onFilterSelected(filter: BibliothequeFilter) {
        selectedFilter.value = filter
    }
}
