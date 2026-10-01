package fr.cklla.pellicule.domain.model

/** Identifie un épisode au sein d'une série/anime suivi (numéro de saison + numéro d'épisode). */
data class EpisodeKey(val seasonNumber: Int, val episodeNumber: Int)
