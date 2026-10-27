package fr.cklla.pellicule.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de `GET /movie/{movie_id}` : seuls le synopsis et la durée (en minutes) sont utiles ici. */
@JsonClass(generateAdapter = true)
data class TmdbMovieDetailsDto(
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
)
