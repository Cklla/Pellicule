package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResponseDto
import fr.cklla.pellicule.data.remote.dto.TmdbSearchResultDto

/** Faux client TMDB en mémoire, pour tester [MediaSearchRepositoryImpl] sans appel réseau réel. */
class FakeTmdbApi : TmdbApi {

    var results: List<TmdbSearchResultDto> = emptyList()
    var shouldThrow = false

    override suspend fun searchMulti(apiKey: String, query: String, language: String, includeAdult: Boolean): TmdbSearchResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return TmdbSearchResponseDto(results = results)
    }
}
