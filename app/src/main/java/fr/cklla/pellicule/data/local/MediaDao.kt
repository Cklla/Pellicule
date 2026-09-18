package fr.cklla.pellicule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
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

    /**
     * Contrairement à [insert] (INSERT OR REPLACE), qui pour une ligne existante fait un DELETE
     * suivi d'un INSERT et déclenche donc la suppression en cascade de ses `watched_episode`, un
     * upsert Room fait un UPDATE quand la ligne existe déjà : les épisodes vus rattachés survivent.
     */
    @Upsert
    suspend fun upsert(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM media")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM media")
    suspend fun clearAll()
}
