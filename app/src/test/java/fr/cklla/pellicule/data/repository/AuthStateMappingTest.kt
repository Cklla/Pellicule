package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.model.AuthUser
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTime::class)
class AuthStateMappingTest {

    private val user = UserInfo(
        aud = "authenticated",
        id = "uuid-a",
        userMetadata = buildJsonObject { put("full_name", "Alice Martin") },
    )

    private fun session(user: UserInfo?) = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = user,
    )

    @Test
    fun `le chargement de la session n'est ni une connexion ni une deconnexion`() {
        assertEquals(AuthState.Initializing, SessionStatus.Initializing.toAuthState(storedUser = user))
    }

    @Test
    fun `une session authentifiee donne l'utilisateur connecte`() {
        val state = SessionStatus.Authenticated(session(user)).toAuthState(storedUser = null)

        assertEquals(AuthState.SignedIn(AuthUser(uid = "uuid-a", displayName = "Alice Martin")), state)
    }

    @Test
    fun `l'absence de session donne l'etat deconnecte`() {
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = false).toAuthState(storedUser = null))
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = true).toAuthState(storedUser = user))
    }

    @Test
    fun `un jeton non renouvelable hors ligne garde l'utilisateur connecte`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(java.io.IOException("hors ligne")))

        assertEquals(
            AuthState.SignedIn(AuthUser(uid = "uuid-a", displayName = "Alice Martin")),
            status.toAuthState(storedUser = user),
        )
    }

    @Test
    fun `un echec de renouvellement sans session enregistree donne l'etat deconnecte`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(java.io.IOException("hors ligne")))

        assertEquals(AuthState.SignedOut, status.toAuthState(storedUser = null))
    }

    @Test
    fun `le nom affiche retombe sur name puis sur rien`() {
        val withName = user.copy(userMetadata = buildJsonObject { put("name", "Alice") })
        val anonymous = user.copy(userMetadata = null)
        val blank = user.copy(userMetadata = buildJsonObject { put("full_name", " ") })

        assertEquals("Alice", withName.toAuthUser().displayName)
        assertEquals(null, anonymous.toAuthUser().displayName)
        assertEquals(null, blank.toAuthUser().displayName)
    }
}
