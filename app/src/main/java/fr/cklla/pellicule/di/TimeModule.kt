package fr.cklla.pellicule.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.domain.util.TimeSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = TimeSource { System.currentTimeMillis() }
}
