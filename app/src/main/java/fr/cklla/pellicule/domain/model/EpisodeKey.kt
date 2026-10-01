package fr.cklla.pellicule.domain.model

/**
 * Identifie un épisode au sein d'une série/anime suivi (numéro de saison + numéro d'épisode).
 * L'ordre naturel est celui de visionnage : par saison, puis par épisode.
 */
data class EpisodeKey(val seasonNumber: Int, val episodeNumber: Int) : Comparable<EpisodeKey> {

    override fun compareTo(other: EpisodeKey): Int =
        compareValuesBy(this, other, EpisodeKey::seasonNumber, EpisodeKey::episodeNumber)
}
