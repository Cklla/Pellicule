package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media

data class BibliothequeUiState(
    val isLoading: Boolean = true,
    val visibleMedia: List<Media> = emptyList(),
    val selectedFilter: BibliothequeFilter = BibliothequeFilter.TOUS,
    val filterCounts: Map<BibliothequeFilter, Int> = emptyMap(),
)
