package fr.cklla.pellicule.domain.model

/** Statut de diffusion d'une série chez TMDB, réduit à ce dont l'app a besoin. */
enum class TvShowStatus {
    EN_DIFFUSION,
    TERMINEE,
    ANNULEE,
    INCONNU,
    ;

    /** Plus aucun épisode ne sortira : la série peut être considérée comme complète une fois tout vu. */
    val isFinished: Boolean get() = this == TERMINEE || this == ANNULEE

    companion object {
        fun fromTmdb(value: String?): TvShowStatus = when (value) {
            "Ended" -> TERMINEE
            "Canceled" -> ANNULEE
            "Returning Series", "In Production", "Planned", "Pilot" -> EN_DIFFUSION
            else -> INCONNU
        }
    }
}

/** Un épisode repéré par TMDB au niveau de la série (dernier diffusé, prochain à diffuser). */
data class EpisodeAirInfo(
    val key: EpisodeKey,
    val airDate: AirDate?,
    val title: String?,
)

/** Titre et date de diffusion d'un épisode, connus pour les saisons dont le détail a été chargé. */
data class EpisodeDetail(
    val title: String?,
    val airDate: AirDate?,
)

/**
 * Métadonnées d'une série/anime conservées localement pour calculer le prochain épisode sans
 * interroger TMDB à chaque affichage.
 *
 * @param seasonEpisodeCounts nombre d'épisodes par numéro de saison (la saison 0, les "spéciaux",
 *   y figure si TMDB la liste, mais n'entre jamais dans le calcul du prochain épisode).
 * @param lastAired dernier épisode diffusé selon TMDB.
 * @param nextToAir prochain épisode annoncé par TMDB, `null` si aucun n'est programmé.
 * @param episodes détail (titre, date) des seuls épisodes des saisons déjà chargées.
 */
data class TvShowInfo(
    val tmdbId: Long,
    val status: TvShowStatus,
    val seasonEpisodeCounts: Map<Int, Int>,
    val lastAired: EpisodeAirInfo?,
    val nextToAir: EpisodeAirInfo?,
    val episodes: Map<EpisodeKey, EpisodeDetail> = emptyMap(),
)
