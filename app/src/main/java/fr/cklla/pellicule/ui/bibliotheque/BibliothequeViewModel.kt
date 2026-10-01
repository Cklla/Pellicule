package fr.cklla.pellicule.ui.bibliotheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.MediaType
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
    private val selectedWatchedYear = MutableStateFlow<Int?>(null)
    private val selectedType = MutableStateFlow<MediaType?>(null)

    val uiState: StateFlow<BibliothequeUiState> = combine(
        mediaRepository.observeMedia(),
        selectedFilter,
        selectedWatchedYear,
        selectedType,
    ) { media, filter, watchedYear, type ->
        BibliothequeUiState(
            isLoading = false,
            visibleMedia = filterMedia(media, filter, watchedYear, type),
            selectedFilter = filter,
            filterCounts = countByFilter(media),
            availableWatchedYears = availableWatchedYears(media),
            selectedWatchedYear = watchedYear,
            selectedType = type,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliothequeUiState(),
    )

    fun onFilterSelected(filter: BibliothequeFilter) {
        selectedFilter.value = filter
        // Le filtre par année n'a de sens que sous "Vu" (voir `filterMedia`) : changer de filtre
        // de statut repart d'une sélection d'année propre plutôt que de garder un choix invisible.
        // Le filtre par type, lui, s'applique sur tous les onglets et reste donc actif.
        selectedWatchedYear.value = null
    }

    /** Re-sélectionner l'année déjà active la désélectionne (retour à "Toutes les années"). */
    fun onWatchedYearSelected(year: Int?) {
        selectedWatchedYear.value = if (year != null && selectedWatchedYear.value == year) null else year
    }

    /** Re-sélectionner le type déjà actif le désélectionne (retour à "Tous les types"). */
    fun onTypeSelected(type: MediaType?) {
        selectedType.value = if (type != null && selectedType.value == type) null else type
    }
}
