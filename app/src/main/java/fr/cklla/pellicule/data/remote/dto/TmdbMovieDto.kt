package fr.cklla.pellicule.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de `GET /movie/{movie_id}` : seul le synopsis est utile ici. */
@JsonClass(generateAdapter = true)
data class TmdbMovieDetailsDto(
    @Json(name = "overview") val overview: String? = null,
)
