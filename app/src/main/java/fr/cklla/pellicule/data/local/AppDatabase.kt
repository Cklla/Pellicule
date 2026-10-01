package fr.cklla.pellicule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.cklla.pellicule.data.local.entity.MediaEntity
import fr.cklla.pellicule.data.local.entity.TvEpisodeCacheEntity
import fr.cklla.pellicule.data.local.entity.TvSeasonCacheEntity
import fr.cklla.pellicule.data.local.entity.TvShowCacheEntity
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity

@Database(
    entities = [
        MediaEntity::class,
        WatchedEpisodeEntity::class,
        TvShowCacheEntity::class,
        TvSeasonCacheEntity::class,
        TvEpisodeCacheEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun tvShowCacheDao(): TvShowCacheDao
}
