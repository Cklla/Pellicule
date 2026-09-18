package fr.cklla.pellicule.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.pellicule.domain.model.JellyfinSession
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implémentation [JellyfinSessionStore] par `SharedPreferences` chiffrées : contrairement à la
 * clé API TMDB (secret de build), c'est un token utilisateur réel donnant accès à un serveur
 * média personnel — un token qui fuiterait exposerait le serveur et l'historique de visionnage de
 * quelqu'un. Pas le mot de passe : le token Jellyfin n'expire pas par défaut, un 401 invalide la
 * session et redemande une connexion (voir `JellyfinRepositoryImpl`).
 */
@Singleton
class JellyfinSessionStoreImpl @Inject constructor(@ApplicationContext context: Context) : JellyfinSessionStore {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _session = MutableStateFlow(readSession())
    override val session: StateFlow<JellyfinSession?> = _session.asStateFlow()

    override val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE_ID, it) }
        }

    override fun save(session: JellyfinSession) {
        prefs.edit {
            putString(KEY_SERVER_URL, session.serverUrl)
            putString(KEY_USER_ID, session.userId)
            putString(KEY_USERNAME, session.username)
            putString(KEY_ACCESS_TOKEN, session.accessToken)
        }
        _session.value = session
    }

    override fun clear() {
        prefs.edit { clear() }
        _session.value = null
    }

    private fun readSession(): JellyfinSession? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return JellyfinSession(serverUrl, userId, username, accessToken)
    }

    private companion object {
        const val PREFS_NAME = "jellyfin_session"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_DEVICE_ID = "device_id"
    }
}
