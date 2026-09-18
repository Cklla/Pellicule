package fr.cklla.pellicule.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.data.local.AppDatabase
import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.MIGRATION_1_2
import fr.cklla.pellicule.data.local.MediaDao
import javax.inject.Singleton

/** Fournit la base Room, unique pour toute la durée de vie de l'application. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pellicule.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideEpisodeDao(database: AppDatabase): EpisodeDao = database.episodeDao()
}
