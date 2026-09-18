package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import fr.cklla.pellicule.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media ORDER BY title ASC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    fun observeById(id: String): Flow<MediaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Update
    suspend fun update(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)
}
