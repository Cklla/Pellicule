package fr.cklla.pellicule.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit les instances Firebase utilisées par l'app.
 *
 * `FirebaseAuth.getInstance()`/`FirebaseFirestore.getInstance()` lisent leur configuration
 * (projet, clé API...) dans `google-services.json` via le plugin Gradle `google-services` — rien
 * à paramétrer manuellement ici, contrairement à Retrofit/TMDB.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // Volontairement pas @Singleton, contrairement à FirebaseAuth : terminer une instance
    // Firestore (purge du cache local à la déconnexion) la rend inutilisable pour toujours, il
    // faut donc pouvoir en redemander une neuve. `getInstance()` s'en charge et met lui-même en
    // cache l'instance courante, on ne crée donc pas un client par injection.
    @Provides
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
