package fr.cklla.pellicule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.cklla.pellicule.data.local.entity.MediaEntity
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity

@Database(entities = [MediaEntity::class, WatchedEpisodeEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
}
