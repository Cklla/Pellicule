package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM watched_episode WHERE mediaId = :mediaId")
    fun observeWatched(mediaId: String): Flow<List<WatchedEpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markWatched(episode: WatchedEpisodeEntity)

    @Query("DELETE FROM watched_episode WHERE mediaId = :mediaId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun markUnwatched(mediaId: String, seasonNumber: Int, episodeNumber: Int)

    @Query("DELETE FROM watched_episode WHERE mediaId = :mediaId")
    suspend fun deleteAllForMedia(mediaId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<WatchedEpisodeEntity>)

    /** Remplace l'intégralité du set d'épisodes vus d'un contenu (utilisé par la synchro Jellyfin, qui fait foi côté lecture). */
    @Transaction
    suspend fun replaceAllForMedia(mediaId: String, episodes: List<WatchedEpisodeEntity>) {
        deleteAllForMedia(mediaId)
        insertAll(episodes)
    }
}
