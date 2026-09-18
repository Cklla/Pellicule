package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbSearchResponseDto
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResultDto
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.MediaType

/**
 * Conversions entre les DTO TMDB et le modèle métier [MediaSearchResult].
 *
 * Isolées dans des fonctions pures (hors Repository) pour rester testables sans appel réseau.
 */

fun TmdbSearchResponseDto.toDomain(): List<MediaSearchResult> = results.mapNotNull { it.toDomain() }

/** `null` pour les résultats "personne" ou sans titre exploitable (recherche multi-type TMDB). */
fun TmdbSearchResultDto.toDomain(): MediaSearchResult? {
    val type = classifyMediaType(this) ?: return null
    val resolvedTitle = (title ?: name)?.takeIf { it.isNotBlank() } ?: return null

    return MediaSearchResult(
        tmdbId = id,
        title = resolvedTitle,
        type = type,
        year = extractYear(releaseDate ?: firstAirDate),
        posterUrl = posterPath?.let { TmdbApi.POSTER_BASE_URL + it },
    )
}

/** TMDB renvoie une date complète ("2023-05-12") ou `null` pour un contenu sans date connue. */
fun extractYear(date: String?): Int? = date?.take(4)?.toIntOrNull()

private const val ANIMATION_GENRE_ID = 16
private const val JAPANESE_LANGUAGE = "ja"
private const val JAPAN_COUNTRY = "JP"

/**
 * TMDB n'a pas de type de contenu "anime" dédié : un anime y est un film ou une série classé dans
 * le genre Animation, d'origine japonaise. Heuristique imparfaite (rate un anime sans le genre
 * Animation renseigné, classe à tort une animation japonaise non-anime) mais suffisante pour
 * distinguer l'essentiel sans dépendance à une seconde API pour l'instant.
 */
private fun classifyMediaType(dto: TmdbSearchResultDto): MediaType? {
    val isAnime = dto.genreIds.orEmpty().contains(ANIMATION_GENRE_ID) &&
        (dto.originalLanguage == JAPANESE_LANGUAGE || dto.originCountry.orEmpty().contains(JAPAN_COUNTRY))
    return when (dto.mediaType) {
        "movie" -> if (isAnime) MediaType.ANIME else MediaType.FILM
        "tv" -> if (isAnime) MediaType.ANIME else MediaType.SERIE
        else -> null
    }
}
