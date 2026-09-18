package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.data.remote.toDomain
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.MediaSearchRepository
import javax.inject.Inject

/** Implémentation TMDB du [MediaSearchRepository]. */
class MediaSearchRepositoryImpl @Inject constructor(
    private val tmdbApi: TmdbApi,
) : MediaSearchRepository {

    override suspend fun searchMedia(query: String): Resource<List<MediaSearchResult>> {
        if (query.isBlank()) return Resource.Success(emptyList())

        return runCatching {
            tmdbApi.searchMulti(apiKey = BuildConfig.TMDB_API_KEY, query = query).toDomain()
        }.fold(
            onSuccess = { Resource.Success(it) },
            onFailure = { Resource.Error("Impossible de contacter TMDB. Vérifie ta connexion.", it) },
        )
    }
}
