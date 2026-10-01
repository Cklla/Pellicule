package fr.cklla.pellicule.domain

import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.NextEpisode
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.WatchStatus

/**
 * Détermine l'épisode à voir ensuite : celui qui suit, dans l'ordre saison/épisode, le plus avancé
 * des épisodes déjà vus (ou le premier épisode quand rien n'est vu). Un trou dans l'historique
 * n'est donc pas proposé, pour qu'une série commencée en cours de route ne reste pas bloquée sur
 * son premier épisode.
 *
 * La saison 0 (spéciaux) est ignorée des deux côtés : ses épisodes ne sont jamais proposés et
 * ceux qui seraient cochés n'avancent pas la position.
 */
fun computeNextEpisode(show: TvShowInfo?, watched: Set<EpisodeKey>, today: AirDate): NextEpisode {
    if (show == null) return NextEpisode.Unknown

    val lastWatched = watched.filter { it.seasonNumber >= 1 }.maxOrNull()
    val key = firstEpisodeAfter(show, lastWatched)
        ?: show.nextToAir?.key?.takeIf { it.seasonNumber >= 1 && (lastWatched == null || it > lastWatched) }
        ?: return if (show.status.isFinished) NextEpisode.Completed else NextEpisode.UpToDate

    val title = show.episodes[key]?.title
        ?: show.lastAired?.takeIf { it.key == key }?.title
        ?: show.nextToAir?.takeIf { it.key == key }?.title
    val airDate = airDateOf(show, key)

    val aired = when {
        airDate != null -> airDate <= today
        show.lastAired != null && key <= show.lastAired.key -> true
        else -> show.status.isFinished
    }
    return if (aired) NextEpisode.Available(key, title) else NextEpisode.Upcoming(key, title, airDate)
}

/**
 * Statut global après avoir coché ([watched] à `true`) ou décoché un épisode, [next] étant le
 * prochain épisode recalculé sur les épisodes vus *après* ce changement.
 *
 * Cocher fait progresser (À voir → En cours, et → Vu quand une série terminée n'a plus rien après
 * l'épisode atteint) sans jamais rétrograder ; décocher repasse seulement un contenu Vu à En cours.
 * Une série encore diffusée ne passe jamais à Vu : de nouveaux épisodes peuvent sortir.
 */
fun statusAfterEpisodeChange(current: WatchStatus, watched: Boolean, next: NextEpisode): WatchStatus = when {
    !watched -> if (current == WatchStatus.VU) WatchStatus.EN_COURS else current
    next == NextEpisode.Completed -> WatchStatus.VU
    current == WatchStatus.A_VOIR -> WatchStatus.EN_COURS
    else -> current
}

private fun firstEpisodeAfter(show: TvShowInfo, lastWatched: EpisodeKey?): EpisodeKey? =
    show.seasonEpisodeCounts
        .filter { (season, count) -> season >= 1 && count > 0 }
        .toSortedMap()
        .flatMap { (season, count) -> (1..count).map { EpisodeKey(season, it) } }
        .firstOrNull { lastWatched == null || it > lastWatched }

private fun airDateOf(show: TvShowInfo, key: EpisodeKey): AirDate? =
    show.episodes[key]?.airDate
        ?: show.lastAired?.takeIf { it.key == key }?.airDate
        ?: show.nextToAir?.takeIf { it.key == key }?.airDate
