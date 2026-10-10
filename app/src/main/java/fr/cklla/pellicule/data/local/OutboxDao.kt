package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import fr.cklla.pellicule.data.local.entity.PendingOperationEntity

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    /** Plus ancienne opération en attente : celle à envoyer en premier. */
    @Query("SELECT * FROM pending_operation ORDER BY seq ASC LIMIT 1")
    suspend fun first(): PendingOperationEntity?

    @Query("SELECT * FROM pending_operation ORDER BY seq ASC")
    suspend fun getAll(): List<PendingOperationEntity>

    @Query("DELETE FROM pending_operation WHERE seq = :seq")
    suspend fun delete(seq: Long)

    @Query("DELETE FROM pending_operation WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: String)

    @Query("DELETE FROM pending_operation")
    suspend fun clearAll()
}
