package fr.cklla.pellicule.domain.model

/**
 * Métadonnées TMDB d'un épisode (titre, image), sans statut vu/non-vu : ce dernier est propre au
 * suivi local (voir [EpisodeKey] et `EpisodeRepository`), jamais redemandé à TMDB.
 */
data class EpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val stillUrl: String?,
)
