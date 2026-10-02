package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.TvShowCacheDao
import fr.cklla.pellicule.data.local.entity.TvEpisodeCacheEntity
import fr.cklla.pellicule.data.local.entity.TvSeasonCacheEntity
import fr.cklla.pellicule.data.local.entity.TvShowCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Faux DAO en mémoire du cache des séries, pour tester [TvShowInfoRepositoryImpl] sans base Room réelle. */
class FakeTvShowCacheDao : TvShowCacheDao {

    private val shows = MutableStateFlow<Map<Long, TvShowCacheEntity>>(emptyMap())
    private val seasons = MutableStateFlow<List<TvSeasonCacheEntity>>(emptyList())
    private val episodes = MutableStateFlow<List<TvEpisodeCacheEntity>>(emptyList())

    override fun observeShows(): Flow<List<TvShowCacheEntity>> = shows.map { it.values.toList() }

    override fun observeSeasons(): Flow<List<TvSeasonCacheEntity>> = seasons

    override fun observeEpisodes(): Flow<List<TvEpisodeCacheEntity>> = episodes

    override suspend fun getShow(tmdbId: Long): TvShowCacheEntity? = shows.value[tmdbId]

    override suspend fun getSeasons(tmdbId: Long): List<TvSeasonCacheEntity> = seasons.value.filter { it.tmdbId == tmdbId }

    override suspend fun getEpisodes(tmdbId: Long): List<TvEpisodeCacheEntity> = episodes.value.filter { it.tmdbId == tmdbId }

    override suspend fun insertShow(show: TvShowCacheEntity) {
        shows.update { it + (show.tmdbId to show) }
    }

    override suspend fun insertSeasons(seasons: List<TvSeasonCacheEntity>) {
        this.seasons.update { current ->
            current.filterNot { old -> seasons.any { it.tmdbId == old.tmdbId && it.seasonNumber == old.seasonNumber } } + seasons
        }
    }

    override suspend fun insertEpisodes(episodes: List<TvEpisodeCacheEntity>) {
        this.episodes.update { current ->
            current.filterNot { old ->
                episodes.any {
                    it.tmdbId == old.tmdbId && it.seasonNumber == old.seasonNumber && it.episodeNumber == old.episodeNumber
                }
            } + episodes
        }
    }

    override suspend fun deleteSeasons(tmdbId: Long) {
        seasons.update { list -> list.filterNot { it.tmdbId == tmdbId } }
    }

    override suspend fun deleteEpisodes(tmdbId: Long, seasonNumber: Int) {
        episodes.update { list -> list.filterNot { it.tmdbId == tmdbId && it.seasonNumber == seasonNumber } }
    }

    override suspend fun clearShows() {
        shows.value = emptyMap()
    }

    override suspend fun clearSeasons() {
        seasons.value = emptyList()
    }

    override suspend fun clearEpisodes() {
        episodes.value = emptyList()
    }
}
