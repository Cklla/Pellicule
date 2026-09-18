package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbSearchResponseDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonDto
import fr.cklla.pellicule.data.remote.dto.TmdbTvDetailsDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API TMDB (https://developer.themoviedb.org/reference) : l'endpoint de recherche multi-type
 * (films, séries, personnes), seul besoin pour l'instant côté recherche/ajout au suivi.
 *
 * `language=fr-FR` par défaut : TMDB retombe de lui-même sur le titre original quand aucune
 * traduction française n'existe, donc pas de second appel nécessaire pour le titre.
 */
interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR",
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResponseDto

    /** Détail d'une série (film ou anime "série" chez nous) : utilisé ici pour sa liste de saisons. */
    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
    ): TmdbTvDetailsDto

    /** Liste des épisodes d'une saison donnée. */
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("tv_id") tvId: Long,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
    ): TmdbSeasonDto

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"

        /** Taille d'affiche adaptée aux cartes/résultats de recherche (voir grille de tailles TMDB). */
        const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w342"

        /** Taille d'image adaptée aux vignettes d'épisode (plus petites qu'une affiche). */
        const val STILL_BASE_URL = "https://image.tmdb.org/t/p/w300"
    }
}
