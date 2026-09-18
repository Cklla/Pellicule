package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
