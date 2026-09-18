package fr.cklla.pellicule.data.remote.jellyfin.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de `GET /Shows/{seriesId}/Episodes` : tous les épisodes d'une série, toutes saisons confondues. */
@JsonClass(generateAdapter = true)
data class JellyfinEpisodesResponseDto(
    @Json(name = "Items") val items: List<JellyfinEpisodeDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class JellyfinEpisodeDto(
    @Json(name = "Id") val id: String,
    /** Numéro de saison. */
    @Json(name = "ParentIndexNumber") val seasonNumber: Int? = null,
    /** Numéro d'épisode au sein de la saison. */
    @Json(name = "IndexNumber") val episodeNumber: Int? = null,
    @Json(name = "UserData") val userData: JellyfinUserDataDto? = null,
)

@JsonClass(generateAdapter = true)
data class JellyfinUserDataDto(
    @Json(name = "Played") val played: Boolean = false,
)
