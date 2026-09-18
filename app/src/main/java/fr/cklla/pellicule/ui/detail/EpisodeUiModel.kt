package fr.cklla.pellicule.ui.detail

/** Épisode d'une saison, tel qu'affiché sur l'écran Détail : métadonnées TMDB + statut vu/non-vu local. */
data class EpisodeUiModel(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val stillUrl: String?,
    val watched: Boolean,
)
