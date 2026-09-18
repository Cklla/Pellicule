package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.repository.TvDetailsRepository

/** Faux repository TMDB (saisons/épisodes), pour tester [DetailViewModel] sans appel réseau réel. */
class FakeTvDetailsRepository : TvDetailsRepository {

    var seasonsResponse: Resource<List<Season>> = Resource.Success(emptyList())
    var episodesResponseBySeason: Map<Int, Resource<List<EpisodeInfo>>> = emptyMap()
    var defaultEpisodesResponse: Resource<List<EpisodeInfo>> = Resource.Success(emptyList())

    override suspend fun getSeasons(tmdbId: Long): Resource<List<Season>> = seasonsResponse

    override suspend fun getEpisodes(tmdbId: Long, seasonNumber: Int): Resource<List<EpisodeInfo>> =
        episodesResponseBySeason[seasonNumber] ?: defaultEpisodesResponse
}
