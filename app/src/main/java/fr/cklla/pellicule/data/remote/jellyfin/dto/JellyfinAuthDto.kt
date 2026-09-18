package fr.cklla.pellicule.data.remote.jellyfin.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Corps de `POST /Users/AuthenticateByName`. */
@JsonClass(generateAdapter = true)
data class JellyfinAuthRequestDto(
    @Json(name = "Username") val username: String,
    @Json(name = "Pw") val password: String,
)

/** Réponse de `POST /Users/AuthenticateByName` : token de session + utilisateur authentifié. */
@JsonClass(generateAdapter = true)
data class JellyfinAuthResponseDto(
    @Json(name = "AccessToken") val accessToken: String,
    @Json(name = "User") val user: JellyfinUserDto,
)

@JsonClass(generateAdapter = true)
data class JellyfinUserDto(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String,
)
