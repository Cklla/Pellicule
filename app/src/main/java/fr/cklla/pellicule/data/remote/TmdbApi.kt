package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API TMDB (https://developer.themoviedb.org/reference) : l'endpoint de recherche multi-type
 * (films, séries, personnes), seul besoin pour l'instant côté recherche/ajout au suivi.
 *
 * `language=fr-FR` par défaut (voir CLAUDE.md) : TMDB retombe de lui-même sur le titre original
 * quand aucune traduction française n'existe, donc pas de second appel nécessaire pour le titre.
 */
interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR",
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResponseDto

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"

        /** Taille d'affiche adaptée aux cartes/résultats de recherche (voir grille de tailles TMDB). */
        const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w342"
    }
}
