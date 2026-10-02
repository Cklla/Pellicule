package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import fr.cklla.pellicule.data.local.entity.EpisodeReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeReminderDao {

    @Query("SELECT * FROM episode_reminder WHERE mediaId = :mediaId")
    fun observe(mediaId: String): Flow<EpisodeReminderEntity?>

    @Query("SELECT * FROM episode_reminder WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): EpisodeReminderEntity?

    @Query("SELECT * FROM episode_reminder WHERE enabled = 1")
    suspend fun getEnabled(): List<EpisodeReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: EpisodeReminderEntity)

    @Query(
        "UPDATE episode_reminder SET lastNotifiedSeason = :seasonNumber, lastNotifiedEpisode = :episodeNumber " +
            "WHERE mediaId = :mediaId",
    )
    suspend fun markNotified(mediaId: String, seasonNumber: Int, episodeNumber: Int)

    /** Active ou éteint le rappel d'un contenu en conservant le dernier épisode notifié. */
    @Transaction
    suspend fun setEnabled(mediaId: String, enabled: Boolean) {
        val current = get(mediaId) ?: EpisodeReminderEntity(mediaId, enabled, null, null)
        upsert(current.copy(enabled = enabled))
    }
}
