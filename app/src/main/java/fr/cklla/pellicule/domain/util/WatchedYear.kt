package fr.cklla.pellicule.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Année la plus ancienne proposée quand l'année de sortie d'un contenu est inconnue. */
const val EARLIEST_WATCHED_YEAR = 1950

/**
 * Années que l'on peut attribuer comme année de visionnage, de la plus récente à la plus ancienne :
 * de l'année en cours jusqu'à l'année de sortie du contenu ([EARLIEST_WATCHED_YEAR] si elle est
 * inconnue). Un contenu sorti dans le futur (date TMDB annoncée, déjà marqué vu) reste rattaché à
 * l'année en cours : on ne propose jamais d'année future.
 */
fun selectableWatchedYears(releaseYear: Int?, timeSource: TimeSource): List<Int> {
    val currentYear = yearOf(timeSource.nowMillis(), timeSource.zone)
    return (currentYear downTo earliestWatchedYear(releaseYear, currentYear)).toList()
}

/**
 * Horodatage (epoch millis) à enregistrer pour qu'un contenu soit classé dans l'année [year], ou
 * `null` si cette année n'est pas permise (future, ou antérieure à la sortie du contenu) : l'appelant
 * ne doit alors rien écrire.
 *
 * - si [currentWatchedAt] tombe déjà dans [year], il est conservé tel quel (choisir l'année déjà
 *   enregistrée ne déplace pas la date) ;
 * - année en cours sinon : l'instant présent ;
 * - année passée sinon : le 1er juillet à midi dans le fuseau de l'horloge. Le milieu de l'année
 *   laisse 6 mois de marge de chaque côté, donc relire l'année de cet horodatage dans un fuseau
 *   quelconque (de UTC-12 à UTC+14) redonne toujours [year].
 */
fun watchedAtForYear(year: Int, currentWatchedAt: Long?, releaseYear: Int?, timeSource: TimeSource): Long? {
    val zone = timeSource.zone
    val now = timeSource.nowMillis()
    val currentYear = yearOf(now, zone)
    if (year > currentYear || year < earliestWatchedYear(releaseYear, currentYear)) return null

    return when {
        currentWatchedAt != null && yearOf(currentWatchedAt, zone) == year -> currentWatchedAt
        year == currentYear -> now
        else -> ZonedDateTime.of(year, 7, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    }
}

private fun earliestWatchedYear(releaseYear: Int?, currentYear: Int): Int =
    minOf(releaseYear ?: EARLIEST_WATCHED_YEAR, currentYear)

private fun yearOf(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).year
