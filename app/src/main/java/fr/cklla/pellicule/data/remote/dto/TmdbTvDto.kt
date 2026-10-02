package fr.cklla.pellicule.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Réponse de `GET /tv/{tv_id}` : liste des saisons, synopsis, statut de diffusion et épisodes
 * charnières (dernier diffusé, prochain annoncé).
 */
@JsonClass(generateAdapter = true)
data class TmdbTvDetailsDto(
    @Json(name = "seasons") val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "last_episode_to_air") val lastEpisodeToAir: TmdbEpisodeToAirDto? = null,
    @Json(name = "next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeToAirDto? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeToAirDto(
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummaryDto(
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "episode_count") val episodeCount: Int,
    @Json(name = "poster_path") val posterPath: String? = null,
)

/** Réponse de `GET /tv/{tv_id}/season/{season_number}` : liste des épisodes de la saison. */
@JsonClass(generateAdapter = true)
data class TmdbSeasonDto(
    @Json(name = "episodes") val episodes: List<TmdbEpisodeDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
)
