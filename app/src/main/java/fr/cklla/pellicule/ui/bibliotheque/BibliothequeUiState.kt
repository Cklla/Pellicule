package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media

data class BibliothequeUiState(
    val isLoading: Boolean = true,
    val visibleMedia: List<Media> = emptyList(),
    val selectedFilter: BibliothequeFilter = BibliothequeFilter.TOUS,
    val filterCounts: Map<BibliothequeFilter, Int> = emptyMap(),
    /** Années disponibles pour le filtre par année de visionnage, non vide seulement sous "Vu". */
    val availableWatchedYears: List<Int> = emptyList(),
    val selectedWatchedYear: Int? = null,
)
