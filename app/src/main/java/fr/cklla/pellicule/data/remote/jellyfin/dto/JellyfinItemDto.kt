package fr.cklla.pellicule.data.remote.jellyfin.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de `GET /Users/{userId}/Items` (liste paginée d'items de la bibliothèque). */
@JsonClass(generateAdapter = true)
data class JellyfinItemsResponseDto(
    @Json(name = "Items") val items: List<JellyfinItemDto> = emptyList(),
)

/**
 * Un item de bibliothèque (série, film...), utilisé ici uniquement pour résoudre l'id Jellyfin
 * d'une série suivie à partir de son id TMDB (voir [providerIds], clé `"Tmdb"`).
 */
@JsonClass(generateAdapter = true)
data class JellyfinItemDto(
    @Json(name = "Id") val id: String,
    @Json(name = "ProviderIds") val providerIds: Map<String, String>? = null,
)
