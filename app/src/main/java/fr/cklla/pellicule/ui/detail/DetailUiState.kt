package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.calendar.NextAiring
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.bibliotheque.watchedYear

data class DetailUiState(
    val isLoading: Boolean = true,
    val media: Media? = null,
    /** Synopsis TMDB, récupéré à l'ouverture de la fiche ; `null` tant qu'il n'est pas encore chargé. */
    val synopsis: String? = null,
    /** Plateformes de streaming françaises ; `null` tant que la réponse TMDB n'est pas arrivée. */
    val watchAvailability: WatchAvailability? = null,
    /**
     * Prochaine diffusion d'une série/anime encore en cours, `null` si terminée, si TMDB n'annonce
     * aucune date ou pour un film : la section « Prochaine diffusion » n'est alors pas affichée.
     */
    val nextAiring: NextAiring? = null,
    /** Rappel « me prévenir à chaque sortie » actif pour ce contenu (toujours faux avant son ajout au suivi). */
    val reminderEnabled: Boolean = false,
    /** Saisons disponibles pour une série/anime ; toujours vide pour un FILM. */
    val seasons: List<Season> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val episodes: List<EpisodeUiModel> = emptyList(),
    val episodesLoading: Boolean = false,
    val episodesErrorMessage: String? = null,
    /** Années proposées par le choix d'année de visionnage (récente d'abord) ; vide hors [canEditWatchedYear]. */
    val watchedYearChoices: List<Int> = emptyList(),
) {
    /**
     * Vrai une fois le contenu réellement présent dans le suivi (id non vide). Faux quand la
     * fiche est ouverte en aperçu depuis un résultat de Recherche pas encore ajouté (voir
     * `DetailViewModel`) : dans ce cas, `DetailScreen` masque statut/retrait/épisodes et affiche
     * un bouton "Ajouter" à la place.
     */
    val isInBacklog: Boolean get() = !media?.id.isNullOrEmpty()

    /** Vrai quand la ligne « Vu en … » est affichée : contenu suivi, au statut Vu. */
    val canEditWatchedYear: Boolean get() = media.canEditWatchedYear()

    /** Année de visionnage enregistrée, ou `null` si inconnue (contenu passé en Vu avant que la date soit conservée). */
    val watchedYear: Int? get() = media?.let(::watchedYear)
}

/** Seul un contenu réellement suivi et au statut Vu a une année de visionnage modifiable. */
internal fun Media?.canEditWatchedYear(): Boolean =
    this != null && id.isNotEmpty() && status == WatchStatus.VU
