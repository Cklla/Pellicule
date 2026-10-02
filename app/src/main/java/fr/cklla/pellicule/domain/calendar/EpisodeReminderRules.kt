package fr.cklla.pellicule.domain.calendar

import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus

/**
 * Vrai si [episode] doit déclencher la notification du jour : il sort aujourd'hui, la série n'est
 * pas terminée et il est plus récent que [lastNotified] (une seule notification par épisode).
 *
 * Seul le jour même compte : la veille, il est trop tôt ; le lendemain, « sort aujourd'hui » serait
 * faux et un épisode manqué n'est pas rattrapé. [today] est toujours fourni par l'appelant.
 */
fun shouldNotify(
    today: AirDate,
    episode: EpisodeAirInfo?,
    status: TvShowStatus,
    lastNotified: EpisodeKey?,
): Boolean {
    if (episode == null || status.isFinished) return false
    if (episode.airDate != today) return false
    return lastNotified == null || episode.key > lastNotified
}

/**
 * Épisode à annoncer aujourd'hui pour [info], ou `null`. TMDB peut déjà avoir rangé l'épisode du
 * jour dans « dernier diffusé » (sa date est celle du pays d'origine) : les deux sont examinés.
 */
fun episodeToNotify(today: AirDate, info: TvShowInfo, lastNotified: EpisodeKey?): EpisodeAirInfo? =
    listOfNotNull(info.nextToAir, info.lastAired)
        .firstOrNull { shouldNotify(today, it, info.status, lastNotified) }

/** Prochaine diffusion affichable sur la fiche : un épisode et sa date, jamais dans le passé. */
data class NextAiring(val key: EpisodeKey, val airDate: AirDate, val title: String?)

/**
 * La prochaine diffusion à montrer pour [info], ou `null` si la série est terminée, si TMDB
 * n'annonce aucune date, ou si la date annoncée est déjà passée (donnée TMDB pas encore mise à
 * jour : afficher ce jour comme « prochain » serait trompeur).
 */
fun upcomingAiring(today: AirDate, info: TvShowInfo?): NextAiring? {
    if (info == null || info.status.isFinished) return null
    val next = info.nextToAir ?: return null
    val airDate = next.airDate ?: return null
    if (airDate < today) return null
    return NextAiring(next.key, airDate, next.title)
}
