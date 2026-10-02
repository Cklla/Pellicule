package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.NextEpisode

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
    /** Prochain épisode des séries/anime En cours, par id de contenu ; absent pour les autres contenus. */
    val nextEpisodes: Map<String, NextEpisode> = emptyMap(),
)
