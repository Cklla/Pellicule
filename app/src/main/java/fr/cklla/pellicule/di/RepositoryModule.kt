package fr.cklla.pellicule.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.data.local.JellyfinSessionStore
import fr.cklla.pellicule.data.local.JellyfinSessionStoreImpl
import fr.cklla.pellicule.data.repository.EpisodeRepositoryImpl
import fr.cklla.pellicule.data.repository.JellyfinRepositoryImpl
import fr.cklla.pellicule.data.repository.MediaRepositoryImpl
import fr.cklla.pellicule.data.repository.MediaSearchRepositoryImpl
import fr.cklla.pellicule.data.repository.SynopsisRepositoryImpl
import fr.cklla.pellicule.data.repository.TvDetailsRepositoryImpl
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.MediaSearchRepository
import fr.cklla.pellicule.domain.repository.SynopsisRepository
import fr.cklla.pellicule.domain.repository.TvDetailsRepository
import javax.inject.Singleton

/** Lie les interfaces de repository à leur implémentation concrète, pour que les ViewModels ne
 * dépendent jamais d'une classe concrète. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindMediaSearchRepository(impl: MediaSearchRepositoryImpl): MediaSearchRepository

    @Binds
    @Singleton
    abstract fun bindEpisodeRepository(impl: EpisodeRepositoryImpl): EpisodeRepository

    @Binds
    @Singleton
    abstract fun bindTvDetailsRepository(impl: TvDetailsRepositoryImpl): TvDetailsRepository

    @Binds
    @Singleton
    abstract fun bindSynopsisRepository(impl: SynopsisRepositoryImpl): SynopsisRepository

    @Binds
    @Singleton
    abstract fun bindJellyfinRepository(impl: JellyfinRepositoryImpl): JellyfinRepository

    @Binds
    @Singleton
    abstract fun bindJellyfinSessionStore(impl: JellyfinSessionStoreImpl): JellyfinSessionStore
}
