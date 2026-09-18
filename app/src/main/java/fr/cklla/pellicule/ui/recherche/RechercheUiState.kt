package fr.cklla.pellicule.ui.recherche

import fr.cklla.pellicule.domain.model.MediaSearchResult

data class RechercheUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<MediaSearchResult> = emptyList(),
    val errorMessage: String? = null,
    /**
     * Id du contenu suivi par tmdbId : sert à la fois à griser les résultats déjà ajoutés et à
     * retrouver l'id du contenu suivi quand on ouvre sa fiche depuis un résultat déjà présent
     * dans le suivi (voir [MediaSearchResult.tmdbId]).
     */
    val trackedMediaIdsByTmdbId: Map<Long, String> = emptyMap(),
)
