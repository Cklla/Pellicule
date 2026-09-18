package fr.cklla.pellicule.ui.recherche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.toMedia
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.MediaSearchRepository
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * La saisie ([query]) est exposée telle quelle (mise à jour immédiate, pour que le champ de
 * recherche et le passage "suggestions ↔ résultats" réagissent sans délai), tandis que l'appel
 * réseau réel est retardé ([SEARCH_DEBOUNCE_MS]) et annulé/relancé à chaque frappe via
 * `collectLatest` — évite un appel TMDB par caractère tapé.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class RechercheViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaSearchRepository: MediaSearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val searchResult = MutableStateFlow<Resource<List<MediaSearchResult>>>(Resource.Success(emptyList()))

    init {
        viewModelScope.launch {
            query
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { currentQuery ->
                    if (currentQuery.isBlank()) {
                        isSearching.value = false
                        searchResult.value = Resource.Success(emptyList())
                        return@collectLatest
                    }
                    isSearching.value = true
                    searchResult.value = mediaSearchRepository.searchMedia(currentQuery)
                    isSearching.value = false
                }
        }
    }

    val uiState: StateFlow<RechercheUiState> = combine(
        query,
        isSearching,
        searchResult,
        mediaRepository.observeMedia(),
    ) { currentQuery, searching, result, tracked ->
        RechercheUiState(
            query = currentQuery,
            isSearching = searching,
            results = (result as? Resource.Success)?.data.orEmpty(),
            errorMessage = (result as? Resource.Error)?.message,
            trackedMediaIdsByTmdbId = tracked.mapNotNull { media -> media.tmdbId?.let { it to media.id } }.toMap(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RechercheUiState(),
    )

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
        // Passe isSearching à true dès la frappe (pas d'attente du debounce) pour éviter un flash
        // de l'état "aucun résultat"/erreur pendant les 300ms de délai.
        if (newQuery.isNotBlank()) isSearching.value = true
    }

    fun onSuggestionSelected(suggestion: String) = onQueryChanged(suggestion)

    fun onAddMedia(result: MediaSearchResult) {
        viewModelScope.launch { mediaRepository.addMedia(result.toMedia()) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
