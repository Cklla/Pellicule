package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeDto
import fr.cklla.pellicule.data.remote.dto.TmdbMovieDetailsDto
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResponseDto
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResultDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonSummaryDto
import fr.cklla.pellicule.data.remote.dto.TmdbTvDetailsDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersResponseDto

/** Faux client TMDB en mémoire, pour tester les repositories qui en dépendent sans appel réseau réel. */
class FakeTmdbApi : TmdbApi {

    var results: List<TmdbSearchResultDto> = emptyList()
    var seasons: List<TmdbSeasonSummaryDto> = emptyList()
    var episodes: List<TmdbEpisodeDto> = emptyList()
    /** Synopsis par langue (`"fr-FR"`/`"en-US"`), pour tester le repli de langue de [getTvDetails]/[getMovieDetails]. */
    var overviewByLanguage: Map<String, String?> = emptyMap()
    /** Offres par code pays, telles que renvoyées par les endpoints `watch/providers`. */
    var watchProviders = TmdbWatchProvidersResponseDto()
    var shouldThrow = false

    override suspend fun searchMulti(apiKey: String, query: String, language: String, includeAdult: Boolean): TmdbSearchResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbSearchResponseDto(results = results)
    }

    override suspend fun getTvDetails(tvId: Long, apiKey: String, language: String): TmdbTvDetailsDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbTvDetailsDto(seasons = seasons, overview = overviewByLanguage[language])
    }

    override suspend fun getTvSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String): TmdbSeasonDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbSeasonDto(episodes = episodes)
    }

    override suspend fun getMovieDetails(movieId: Long, apiKey: String, language: String): TmdbMovieDetailsDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbMovieDetailsDto(overview = overviewByLanguage[language])
    }

    override suspend fun getMovieWatchProviders(movieId: Long, apiKey: String): TmdbWatchProvidersResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return watchProviders
    }

    override suspend fun getTvWatchProviders(tvId: Long, apiKey: String): TmdbWatchProvidersResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return watchProviders
    }
}
