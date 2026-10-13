package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.model.AuthUser
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun UserInfo.toAuthUser() = AuthUser(uid = id, displayName = userMetadata.displayName())

// Google renseigne `full_name` et `name` ; l'un ou l'autre suffit pour un simple affichage.
private fun JsonObject?.displayName(): String? =
    listOf("full_name", "name").firstNotNullOfOrNull { key ->
        runCatching { this?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
    }

/**
 * Traduit l'état de session du SDK en [AuthState].
 *
 * `RefreshFailure` (jeton expiré qu'on ne peut pas renouveler, typiquement hors ligne) n'est **pas**
 * une déconnexion : la session enregistrée existe toujours et sera renouvelée au retour du réseau.
 * Le SDK n'expose alors plus l'utilisateur, d'où [storedUser], lu dans la session enregistrée :
 * sans lui l'application afficherait l'écran de connexion à un utilisateur hors ligne.
 */
internal fun SessionStatus.toAuthState(storedUser: UserInfo?): AuthState = when (this) {
    is SessionStatus.Initializing -> AuthState.Initializing
    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
    is SessionStatus.Authenticated -> session.user?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut
    is SessionStatus.RefreshFailure -> storedUser?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut
}
