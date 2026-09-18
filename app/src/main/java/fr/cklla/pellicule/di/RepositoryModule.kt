package fr.cklla.pellicule.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.data.repository.MediaRepositoryImpl
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Singleton

/** Lie les interfaces de repository à leur implémentation concrète, pour que les ViewModels ne
 * dépendent jamais d'une classe concrète. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository
}
