package fr.cklla.pellicule.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Réponse de `GET /{type}/{id}/watch/providers` : les offres sont regroupées par code pays, sans
 * paramètre de langue côté requête (on ne lit que l'entrée `FR`).
 */
@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersResponseDto(
    @Json(name = "results") val results: Map<String, TmdbWatchProvidersCountryDto>? = null,
)

/** Offres d'un pays donné, ventilées par type (abonnement, gratuit, publicité, location, achat). */
@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersCountryDto(
    @Json(name = "link") val link: String? = null,
    @Json(name = "flatrate") val flatrate: List<TmdbWatchProviderDto>? = null,
    @Json(name = "free") val free: List<TmdbWatchProviderDto>? = null,
    @Json(name = "ads") val ads: List<TmdbWatchProviderDto>? = null,
    @Json(name = "rent") val rent: List<TmdbWatchProviderDto>? = null,
    @Json(name = "buy") val buy: List<TmdbWatchProviderDto>? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProviderDto(
    @Json(name = "provider_id") val providerId: Int,
    @Json(name = "provider_name") val providerName: String,
    @Json(name = "logo_path") val logoPath: String? = null,
    /** Ordre d'affichage conseillé par TMDB pour le pays demandé (le plus petit d'abord). */
    @Json(name = "display_priority") val displayPriority: Int? = null,
)
