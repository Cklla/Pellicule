package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.data.remote.toDomain
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.repository.TvDetailsRepository
import javax.inject.Inject

/** Implémentation TMDB du [TvDetailsRepository]. */
class TvDetailsRepositoryImpl @Inject constructor(
    private val tmdbApi: TmdbApi,
) : TvDetailsRepository {

    override suspend fun getSeasons(tmdbId: Long): Resource<List<Season>> = runCatching {
        tmdbApi.getTvDetails(tvId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY).toDomain()
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible de contacter TMDB. Vérifie ta connexion.", it) },
    )

    override suspend fun getEpisodes(tmdbId: Long, seasonNumber: Int): Resource<List<EpisodeInfo>> = runCatching {
        tmdbApi.getTvSeason(tvId = tmdbId, seasonNumber = seasonNumber, apiKey = BuildConfig.TMDB_API_KEY).toDomain()
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible de contacter TMDB. Vérifie ta connexion.", it) },
    )
}
