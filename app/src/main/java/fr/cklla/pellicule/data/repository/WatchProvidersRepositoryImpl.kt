package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.data.remote.toDomain
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.repository.WatchProvidersRepository
import javax.inject.Inject

/**
 * Implémentation TMDB du [WatchProvidersRepository], qui sert les données JustWatch. Comme pour le
 * synopsis, une série/anime passe par `GET /tv/{id}/watch/providers` et un film par
 * `GET /movie/{id}/watch/providers` : un anime classé FILM chez nous n'a pas de fiche `/tv`.
 */
class WatchProvidersRepositoryImpl @Inject constructor(
    private val tmdbApi: TmdbApi,
) : WatchProvidersRepository {

    override suspend fun getAvailability(tmdbId: Long, type: MediaType): Resource<WatchAvailability> = runCatching {
        if (type == MediaType.FILM) {
            tmdbApi.getMovieWatchProviders(movieId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY)
        } else {
            tmdbApi.getTvWatchProviders(tvId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY)
        }.toDomain()
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible de contacter TMDB. Vérifie ta connexion.", it) },
    )
}
