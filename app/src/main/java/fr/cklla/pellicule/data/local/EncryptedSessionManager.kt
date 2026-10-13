package fr.cklla.pellicule.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Conserve la session Supabase (jeton d'accès et jeton de renouvellement, qui donnent accès au
 * suivi du compte) dans des préférences chiffrées, à la place du stockage en clair du SDK.
 *
 * Les préférences sont fournies déjà chiffrées par l'injection de dépendances (voir
 * `SupabaseModule`) ; cette classe ne s'occupe que de la sérialisation.
 */
class EncryptedSessionManager(private val prefs: SharedPreferences) : SessionManager {

    // `commit` plutôt qu'`apply` : un jeton de renouvellement ne sert qu'une fois, et le perdre
    // parce que le processus s'arrête avant l'écriture déconnecterait l'utilisateur.
    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val serialized = json.encodeToString(UserSession.serializer(), session)
        prefs.edit(commit = true) { putString(KEY_SESSION, serialized) }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        val serialized = prefs.getString(KEY_SESSION, null) ?: error("Aucune session enregistrée")
        json.decodeFromString(UserSession.serializer(), serialized)
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) { remove(KEY_SESSION) }
    }

    private companion object {
        const val KEY_SESSION = "session"

        // `encodeDefaults` : la date d'expiration est une valeur par défaut calculée à la création de
        // la session. Ne pas l'écrire la recalculerait à chaque lecture (« maintenant + durée »), et
        // une session périmée paraîtrait valide.
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
