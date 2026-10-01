package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType

data class BibliothequeUiState(
    val isLoading: Boolean = true,
    val visibleMedia: List<Media> = emptyList(),
    val selectedFilter: BibliothequeFilter = BibliothequeFilter.TOUS,
    val filterCounts: Map<BibliothequeFilter, Int> = emptyMap(),
    /** Années disponibles pour le filtre par année de visionnage, non vide seulement sous "Vu". */
    val availableWatchedYears: List<Int> = emptyList(),
    val selectedWatchedYear: Int? = null,
    /** Filtre par type de contenu, `null` = tous les types. S'applique sur tous les onglets. */
    val selectedType: MediaType? = null,
)
