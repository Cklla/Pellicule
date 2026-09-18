package fr.cklla.pellicule.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.pellicule.data.remote.jellyfin.JellyfinApi
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Client Retrofit dédié à Jellyfin, avec une base URL placeholder : contrairement à TMDB, le
 * serveur est renseigné par l'utilisateur à l'exécution (voir `JellyfinSessionStore`), donc
 * chaque appel de [JellyfinApi] passe son URL absolue (`@Url`) plutôt que de dépendre de la base
 * URL Retrofit. Réutilise l'`OkHttpClient`/`Moshi` déjà fournis par [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object JellyfinNetworkModule {

    @Provides
    @Singleton
    fun provideJellyfinApi(okHttpClient: OkHttpClient, moshi: Moshi): JellyfinApi = Retrofit.Builder()
        .baseUrl(JellyfinApi.PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(JellyfinApi::class.java)
}
