package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.MovieRuntimeRepository
import javax.inject.Inject

/**
 * Implémentation TMDB du [MovieRuntimeRepository], via `GET /movie/{id}`. La durée ne dépend pas
 * de la langue : un seul appel, sans repli en `en-US`. TMDB renvoie `0` (ou rien) pour une durée
 * inconnue, ramené à `null` pour ne jamais afficher « 0 min ».
 */
class MovieRuntimeRepositoryImpl @Inject constructor(
    private val tmdbApi: TmdbApi,
) : MovieRuntimeRepository {

    override suspend fun getRuntimeMinutes(tmdbId: Long): Resource<Int?> = runCatching {
        tmdbApi.getMovieDetails(movieId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY).runtime?.takeIf { it > 0 }
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible de contacter TMDB. Vérifie ta connexion.", it) },
    )
}
