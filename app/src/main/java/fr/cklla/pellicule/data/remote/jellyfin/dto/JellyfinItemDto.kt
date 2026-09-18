package fr.cklla.pellicule.data.remote.jellyfin.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de `GET /Users/{userId}/Items` (liste paginée d'items de la bibliothèque). */
@JsonClass(generateAdapter = true)
data class JellyfinItemsResponseDto(
    @Json(name = "Items") val items: List<JellyfinItemDto> = emptyList(),
)

/**
 * Un item de bibliothèque (série, film...). Sert à résoudre l'id Jellyfin d'un contenu suivi à
 * partir de son id TMDB (voir [providerIds], clé `"Tmdb"`), et pour les films, à lire directement
 * le statut de lecture ([userData]) — contrairement aux séries, un film n'a pas de sous-item
 * "épisode" à interroger séparément.
 */
@JsonClass(generateAdapter = true)
data class JellyfinItemDto(
    @Json(name = "Id") val id: String,
    @Json(name = "ProviderIds") val providerIds: Map<String, String>? = null,
    @Json(name = "UserData") val userData: JellyfinUserDataDto? = null,
)
