package fr.cklla.pellicule.domain.model

/** Ce qu'il reste à voir pour une série/anime suivi, d'après les épisodes vus et les métadonnées en cache. */
sealed interface NextEpisode {

    /** Épisode déjà diffusé, que le "+1" peut marquer vu. */
    data class Available(val key: EpisodeKey, val title: String?) : NextEpisode

    /** Épisode pas encore diffusé : [airDate] est `null` quand TMDB n'annonce pas de date. */
    data class Upcoming(val key: EpisodeKey, val title: String?, val airDate: AirDate?) : NextEpisode

    /** Série en cours de diffusion dont tous les épisodes sortis sont vus, sans suite annoncée. */
    data object UpToDate : NextEpisode

    /** Série terminée ou annulée dont le dernier épisode est atteint. */
    data object Completed : NextEpisode

    /** Pas de métadonnées en cache pour cette série (jamais chargées, ou série sans identifiant TMDB). */
    data object Unknown : NextEpisode
}
