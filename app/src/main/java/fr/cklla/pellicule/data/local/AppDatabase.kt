package fr.cklla.pellicule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.cklla.pellicule.data.local.entity.MediaEntity

@Database(entities = [MediaEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
