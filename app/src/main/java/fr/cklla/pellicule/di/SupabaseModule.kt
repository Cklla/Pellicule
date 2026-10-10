package fr.cklla.pellicule.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.local.EncryptedSessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import javax.inject.Singleton

/**
 * Fournit le client Supabase. URL et clé publishable viennent de la configuration de build, jamais
 * du code source : la clé est publique par conception (elle se trouve dans l'APK), c'est le RLS
 * côté base qui protège les données.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    private const val SESSION_PREFS_NAME = "supabase_session"

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        EncryptedSessionManager(
            EncryptedSharedPreferences.create(
                context,
                SESSION_PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ),
        )

    @Provides
    @Singleton
    fun provideSupabaseClient(sessionManager: SessionManager): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ) {
        install(Auth) {
            this.sessionManager = sessionManager
        }
    }
}
