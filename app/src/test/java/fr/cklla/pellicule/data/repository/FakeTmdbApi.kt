package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeDto
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResponseDto
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResultDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonSummaryDto
import fr.cklla.pellicule.data.remote.dto.TmdbTvDetailsDto

/** Faux client TMDB en mémoire, pour tester les repositories qui en dépendent sans appel réseau réel. */
class FakeTmdbApi : TmdbApi {

    var results: List<TmdbSearchResultDto> = emptyList()
    var seasons: List<TmdbSeasonSummaryDto> = emptyList()
    var episodes: List<TmdbEpisodeDto> = emptyList()
    var shouldThrow = false

    override suspend fun searchMulti(apiKey: String, query: String, language: String, includeAdult: Boolean): TmdbSearchResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbSearchResponseDto(results = results)
    }

    override suspend fun getTvDetails(tvId: Long, apiKey: String, language: String): TmdbTvDetailsDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbTvDetailsDto(seasons = seasons)
    }

    override suspend fun getTvSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String): TmdbSeasonDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbSeasonDto(episodes = episodes)
    }
}
