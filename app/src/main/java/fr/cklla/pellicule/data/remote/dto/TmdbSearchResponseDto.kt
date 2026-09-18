package fr.cklla.pellicule.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de l'endpoint de recherche multi-type TMDB (`GET /search/multi`). */
@JsonClass(generateAdapter = true)
data class TmdbSearchResponseDto(
    @Json(name = "results") val results: List<TmdbSearchResultDto> = emptyList(),
)

/**
 * Un résultat de `/search/multi`, film, série ou personne (voir [mediaType]). Les champs
 * titre/date de sortie diffèrent selon le type (`title`/`release_date` pour un film,
 * `name`/`first_air_date` pour une série) : TMDB les renvoie tous sur la même forme de résultat
 * plutôt que des structures distinctes par type.
 */
@JsonClass(generateAdapter = true)
data class TmdbSearchResultDto(
    @Json(name = "id") val id: Long,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "origin_country") val originCountry: List<String>? = null,
)
