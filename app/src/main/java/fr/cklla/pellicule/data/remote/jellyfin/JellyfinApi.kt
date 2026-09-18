package fr.cklla.pellicule.data.remote.jellyfin

import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthRequestDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthResponseDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinEpisodesResponseDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinItemsResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * API Jellyfin (auth + lecture/écriture du statut "vu"). Toutes les URLs sont absolues (voir
 * [Url]) puisque le serveur est renseigné par l'utilisateur à l'exécution : la base URL Retrofit
 * fournie par `JellyfinNetworkModule` n'est qu'un placeholder, jamais utilisée telle quelle.
 *
 * Ce serveur (testé en 12.1.0) n'accepte l'authentification — login comme appels de session —
 * que via l'en-tête `Authorization: MediaBrowser Client="...", ..., Token="..."` : ni
 * `X-Emby-Authorization` (login) ni `X-Emby-Token` (appels authentifiés) ne sont reconnus, ils
 * renvoient respectivement 400 et 401 comme si l'en-tête était absent. Voir
 * `JellyfinRepositoryImpl.authHeader`.
 */
interface JellyfinApi {

    @POST
    suspend fun authenticateByName(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: JellyfinAuthRequestDto,
    ): JellyfinAuthResponseDto

    /** Bibliothèque filtrée par type, avec les `ProviderIds` — utilisé pour résoudre l'id Jellyfin d'une série à partir de son id TMDB. */
    @GET
    suspend fun getItems(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Series",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "ProviderIds",
    ): JellyfinItemsResponseDto

    /** Tous les épisodes d'une série (toutes saisons), avec leur statut "vu" (`JellyfinUserDataDto`). */
    @GET
    suspend fun getSeriesEpisodes(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("userId") userId: String,
        @Query("Fields") fields: String = "UserData",
    ): JellyfinEpisodesResponseDto

    @POST
    suspend fun markPlayed(
        @Url url: String,
        @Header("Authorization") authHeader: String,
    )

    @DELETE
    suspend fun markUnplayed(
        @Url url: String,
        @Header("Authorization") authHeader: String,
    )

    companion object {
        /** Placeholder pour Retrofit.Builder (jamais utilisé : chaque appel passe son [Url] absolu). */
        const val PLACEHOLDER_BASE_URL = "http://localhost/"

        fun authenticateByNameUrl(serverUrl: String) = "${serverUrl.trimEnd('/')}/Users/AuthenticateByName"
        fun itemsUrl(serverUrl: String, userId: String) = "${serverUrl.trimEnd('/')}/Users/$userId/Items"
        fun seriesEpisodesUrl(serverUrl: String, seriesId: String) = "${serverUrl.trimEnd('/')}/Shows/$seriesId/Episodes"
        fun playedItemUrl(serverUrl: String, userId: String, itemId: String) =
            "${serverUrl.trimEnd('/')}/Users/$userId/PlayedItems/$itemId"
    }
}
