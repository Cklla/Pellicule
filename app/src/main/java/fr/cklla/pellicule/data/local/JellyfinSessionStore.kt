package fr.cklla.pellicule.data.local

import fr.cklla.pellicule.domain.model.JellyfinSession
import kotlinx.coroutines.flow.StateFlow

/**
 * Persiste la session Jellyfin (URL serveur, identifiant utilisateur, token d'accès). Interface
 * séparée de [JellyfinSessionStoreImpl] (comme les DAO Room) pour rester substituable par un fake
 * dans les tests de `JellyfinRepositoryImpl` — `EncryptedSharedPreferences` a besoin d'un vrai
 * Android Keystore, indisponible en test unitaire JVM pur.
 */
interface JellyfinSessionStore {
    val session: StateFlow<JellyfinSession?>

    /** Identifiant stable de cette installation, envoyé dans l'en-tête `X-Emby-Authorization` (identifie l'app auprès de Jellyfin, distinct du token d'accès). */
    val deviceId: String

    fun save(session: JellyfinSession)
    fun clear()
}
