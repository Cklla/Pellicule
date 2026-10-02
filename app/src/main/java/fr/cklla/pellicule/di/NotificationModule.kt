package fr.cklla.pellicule.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.domain.notification.EpisodeNotifier
import fr.cklla.pellicule.notification.EpisodeNotificationNotifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindEpisodeNotifier(impl: EpisodeNotificationNotifier): EpisodeNotifier
}
