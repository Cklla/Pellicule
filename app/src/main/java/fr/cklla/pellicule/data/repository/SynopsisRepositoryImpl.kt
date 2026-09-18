package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.SynopsisRepository
import javax.inject.Inject

/**
 * Implémentation TMDB du [SynopsisRepository]. Une série/anime "série" passe par `GET /tv/{id}`,
 * un film par `GET /movie/{id}` — même limite que [fr.cklla.pellicule.domain.repository.TvDetailsRepository]
 * pour les épisodes : un anime classé FILM (film d'animation japonais) n'a pas de fiche `/tv`.
 *
 * `language=fr-FR` par défaut, avec repli en `en-US` si le synopsis revient vide dans cette
 * langue : contrairement au titre, TMDB ne retombe pas toujours de lui-même sur une traduction
 * existante pour ce champ.
 */
class SynopsisRepositoryImpl @Inject constructor(
    private val tmdbApi: TmdbApi,
) : SynopsisRepository {

    override suspend fun getSynopsis(tmdbId: Long, type: MediaType): Resource<String?> = runCatching {
        fetchOverview(tmdbId, type, language = "fr-FR")
            ?.takeIf { it.isNotBlank() }
            ?: fetchOverview(tmdbId, type, language = "en-US")
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible de contacter TMDB. Vérifie ta connexion.", it) },
    )

    private suspend fun fetchOverview(tmdbId: Long, type: MediaType, language: String): String? =
        if (type == MediaType.FILM) {
            tmdbApi.getMovieDetails(movieId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY, language = language).overview
        } else {
            tmdbApi.getTvDetails(tvId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY, language = language).overview
        }
}
