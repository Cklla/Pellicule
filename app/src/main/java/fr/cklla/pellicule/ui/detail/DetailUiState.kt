package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchAvailability

data class DetailUiState(
    val isLoading: Boolean = true,
    val media: Media? = null,
    /** Synopsis TMDB, récupéré à l'ouverture de la fiche ; `null` tant qu'il n'est pas encore chargé. */
    val synopsis: String? = null,
    /** Plateformes de streaming françaises ; `null` tant que la réponse TMDB n'est pas arrivée. */
    val watchAvailability: WatchAvailability? = null,
    /** Saisons disponibles pour une série/anime ; toujours vide pour un FILM. */
    val seasons: List<Season> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val episodes: List<EpisodeUiModel> = emptyList(),
    val episodesLoading: Boolean = false,
    val episodesErrorMessage: String? = null,
) {
    /**
     * Vrai une fois le contenu réellement présent dans le suivi (id non vide). Faux quand la
     * fiche est ouverte en aperçu depuis un résultat de Recherche pas encore ajouté (voir
     * `DetailViewModel`) : dans ce cas, `DetailScreen` masque statut/retrait/épisodes et affiche
     * un bouton "Ajouter" à la place.
     */
    val isInBacklog: Boolean get() = !media?.id.isNullOrEmpty()
}
