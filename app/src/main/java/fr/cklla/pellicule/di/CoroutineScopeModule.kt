package fr.cklla.pellicule.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Qualifie le [CoroutineScope] vivant aussi longtemps que l'application, utilisé par
 * [fr.cklla.pellicule.data.repository.MediaRepositoryImpl] pour piloter la synchro Firestore en
 * arrière-plan (pas de `viewModelScope` disponible pour un singleton Hilt). Injecté plutôt que
 * construit en dur dans la classe pour pouvoir le remplacer par un scope de test déterministe
 * (`UnconfinedTestDispatcher`) dans les tests unitaires.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
