package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.ui.bibliotheque.availableWatchedYears
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val stats: StatsData = StatsData(),
    /** Années de visionnage disponibles, de la plus récente à la plus ancienne. */
    val availableYears: List<Int> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(mediaRepository: MediaRepository) : ViewModel() {

    private val selectedYear = MutableStateFlow<Int?>(null)
    private val selectedType = MutableStateFlow<MediaType?>(null)

    val uiState: StateFlow<StatsUiState> = combine(
        mediaRepository.observeMedia(),
        selectedYear,
        selectedType,
    ) { media, year, type ->
        val years = availableWatchedYears(media)
        // Une année qui n'a plus aucun visionnage (contenu repassé hors de Vu) n'apparaît plus
        // dans les chips : la garder sélectionnée donnerait un filtre invisible.
        StatsUiState(stats = computeStats(media, year?.takeIf { it in years }, type), availableYears = years)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    /** Re-sélectionner l'année déjà active la désélectionne (retour à « Toutes les années »). */
    fun onYearSelected(year: Int?) {
        selectedYear.value = if (year != null && selectedYear.value == year) null else year
    }

    /** Re-sélectionner le type déjà actif le désélectionne (retour à « Tous les types »). */
    fun onTypeSelected(type: MediaType?) {
        selectedType.value = if (type != null && selectedType.value == type) null else type
    }
}
