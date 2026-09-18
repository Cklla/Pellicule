package fr.cklla.pellicule.domain.model

/**
 * Un résultat de recherche TMDB, avant ajout au suivi.
 *
 * Distinct de [Media] : ce modèle ne représente qu'une fiche TMDB, pas encore un contenu suivi
 * (pas de statut de visionnage).
 */
data class MediaSearchResult(
    val tmdbId: Long,
    val title: String,
    val type: MediaType,
    val year: Int?,
    val posterUrl: String?,
)

/** Convertit un résultat de recherche en contenu suivi, avec le statut par défaut "à voir". */
fun MediaSearchResult.toMedia(): Media = Media(
    title = title,
    type = type,
    status = WatchStatus.A_VOIR,
    tmdbId = tmdbId,
    releaseYear = year,
    posterUrl = posterUrl,
)
