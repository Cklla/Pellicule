package fr.cklla.pellicule.data.local

import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTime::class)
class EncryptedSessionManagerTest {

    private val prefs = FakeSharedPreferences()
    private val manager = EncryptedSessionManager(prefs)

    private val session = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = "uuid-a", email = "a@exemple.fr"),
        expiresAt = Instant.fromEpochSeconds(1_700_000_000),
    )

    @Test
    fun `une session enregistree se relit a l'identique`() = runTest {
        manager.saveSession(session)

        assertEquals(session, manager.loadSession())
    }

    @Test
    fun `la date d'expiration enregistree n'est pas recalculee a la lecture`() = runTest {
        // Une session déjà expirée doit le rester une fois relue : sinon elle paraîtrait valide.
        manager.saveSession(session)

        assertEquals(Instant.fromEpochSeconds(1_700_000_000), manager.loadSession().expiresAt)
    }

    @Test
    fun `sans session enregistree le chargement echoue`() = runTest {
        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun `une session supprimee ne se recharge plus`() = runTest {
        manager.saveSession(session)

        manager.deleteSession()

        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun `les ecritures sont synchrones pour ne pas perdre un jeton de renouvellement`() = runTest {
        manager.saveSession(session)
        manager.deleteSession()

        assertEquals(2, prefs.commitCount)
        assertEquals(0, prefs.applyCount)
    }

    @Test
    fun `un contenu illisible est traite comme une absence de session`() = runTest {
        prefs.edit().putString("session", "{pas du json").commit()

        assertNull(manager.loadSessionOrNull())
        assertTrue(prefs.contains("session"))
    }
}
