package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import fr.cklla.pellicule.data.local.entity.TvEpisodeCacheEntity
import fr.cklla.pellicule.data.local.entity.TvSeasonCacheEntity
import fr.cklla.pellicule.data.local.entity.TvShowCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TvShowCacheDao {

    @Query("SELECT * FROM tv_show_cache")
    fun observeShows(): Flow<List<TvShowCacheEntity>>

    @Query("SELECT * FROM tv_season_cache")
    fun observeSeasons(): Flow<List<TvSeasonCacheEntity>>

    @Query("SELECT * FROM tv_episode_cache")
    fun observeEpisodes(): Flow<List<TvEpisodeCacheEntity>>

    @Query("SELECT * FROM tv_show_cache WHERE tmdbId = :tmdbId")
    suspend fun getShow(tmdbId: Long): TvShowCacheEntity?

    @Query("SELECT * FROM tv_season_cache WHERE tmdbId = :tmdbId")
    suspend fun getSeasons(tmdbId: Long): List<TvSeasonCacheEntity>

    @Query("SELECT * FROM tv_episode_cache WHERE tmdbId = :tmdbId")
    suspend fun getEpisodes(tmdbId: Long): List<TvEpisodeCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShow(show: TvShowCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<TvSeasonCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<TvEpisodeCacheEntity>)

    @Query("DELETE FROM tv_season_cache WHERE tmdbId = :tmdbId")
    suspend fun deleteSeasons(tmdbId: Long)

    @Query("DELETE FROM tv_episode_cache WHERE tmdbId = :tmdbId AND seasonNumber = :seasonNumber")
    suspend fun deleteEpisodes(tmdbId: Long, seasonNumber: Int)

    @Query("DELETE FROM tv_show_cache")
    suspend fun clearShows()

    @Query("DELETE FROM tv_season_cache")
    suspend fun clearSeasons()

    @Query("DELETE FROM tv_episode_cache")
    suspend fun clearEpisodes()

    /**
     * Remplace la fiche d'une série et ses nombres d'épisodes par saison. Le détail des épisodes
     * déjà chargé (`tv_episode_cache`) et sa date de chargement sont conservés pour les saisons qui
     * existent toujours : un rafraîchissement de la série ne doit pas obliger à retélécharger les
     * titres.
     */
    @Transaction
    suspend fun replaceShow(show: TvShowCacheEntity, seasons: List<TvSeasonCacheEntity>) {
        val previousFetchTimes = getSeasons(show.tmdbId).associate { it.seasonNumber to it.episodesFetchedAt }
        insertShow(show)
        deleteSeasons(show.tmdbId)
        insertSeasons(seasons.map { it.copy(episodesFetchedAt = previousFetchTimes[it.seasonNumber]) })
    }

    /** Remplace le détail des épisodes d'une saison et note la date de ce chargement. */
    @Transaction
    suspend fun replaceSeasonEpisodes(
        season: TvSeasonCacheEntity,
        episodes: List<TvEpisodeCacheEntity>,
    ) {
        deleteEpisodes(season.tmdbId, season.seasonNumber)
        insertEpisodes(episodes)
        insertSeasons(listOf(season))
    }

    @Transaction
    suspend fun clearAll() {
        clearShows()
        clearSeasons()
        clearEpisodes()
    }
}
