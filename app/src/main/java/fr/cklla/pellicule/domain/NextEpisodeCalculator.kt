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
 * l'épisode atteint). Un contenu Vu ne repasse En cours que si l'épisode coché laisse derrière lui un
 * épisode déjà diffusé : c'est le cas d'une nouvelle saison arrivée après le dernier visionnage.
 * Décocher repasse un contenu Vu à En cours. Une série encore diffusée ne passe jamais à Vu :
 * de nouveaux épisodes peuvent sortir.
 */
fun statusAfterEpisodeChange(current: WatchStatus, watched: Boolean, next: NextEpisode): WatchStatus = when {
    !watched -> if (current == WatchStatus.VU) WatchStatus.EN_COURS else current
    next == NextEpisode.Completed -> WatchStatus.VU
    current == WatchStatus.A_VOIR -> WatchStatus.EN_COURS
    current == WatchStatus.VU && next is NextEpisode.Available -> WatchStatus.EN_COURS
    else -> current
}

/**
 * Vrai quand le serveur expose un épisode (hors saison 0) situé après le dernier épisode vu, qu'il
 * soit vu lui-même ou non : [watched] doit réunir l'historique local et ce que le serveur signale vu.
 * Sans aucun épisode vu il n'y a pas de « dernier » : le résultat est faux, pour qu'un contenu marqué
 * Vu à la main sans historique ne soit pas remis en cours par le simple contenu du serveur.
 */
fun hasEpisodeAfterLastWatched(serverEpisodes: Set<EpisodeKey>, watched: Set<EpisodeKey>): Boolean {
    val lastWatched = watched.filter { it.seasonNumber >= 1 }.maxOrNull() ?: return false
    return serverEpisodes.any { it.seasonNumber >= 1 && it > lastWatched }
}

/**
 * Statut après un pull Jellyfin : le serveur ne fait jamais régresser un statut ([fromJellyfin] ne
 * peut que le faire progresser, ce qui protège d'un serveur réinstallé sans historique), sauf quand
 * un contenu Vu reçoit de nouveaux épisodes ([hasNewEpisodes], voir [hasEpisodeAfterLastWatched]).
 */
fun statusAfterJellyfinPull(current: WatchStatus, fromJellyfin: WatchStatus, hasNewEpisodes: Boolean): WatchStatus = when {
    current == WatchStatus.VU && hasNewEpisodes -> WatchStatus.EN_COURS
    else -> maxOf(current, fromJellyfin)
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
