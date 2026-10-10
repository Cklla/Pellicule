package fr.cklla.pellicule.data.repository

import androidx.credentials.exceptions.GetCredentialUnknownException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInFailureTest {

    private val defaultSink = SignInFailure.sink

    @After
    fun tearDown() {
        SignInFailure.sink = defaultSink
    }

    @Test
    fun `une erreur Credential Manager est decrite par son type et son message`() {
        val description = SignInFailure.describe(GetCredentialUnknownException("Play Services indisponible"))

        assertEquals("TYPE_UNKNOWN - Play Services indisponible", description)
    }

    @Test
    fun `une exception sans message n'affiche que son identifiant`() {
        val description = SignInFailure.describe(IllegalStateException())

        assertEquals("IllegalStateException", description)
    }

    @Test
    fun `un message trop long est tronque pour rester lisible a l'ecran`() {
        val description = SignInFailure.describe(IllegalStateException("x".repeat(500)))

        assertEquals("IllegalStateException - ${"x".repeat(120)}", description)
    }

    @Test
    fun `l'echec est journalise avec l'exception d'origine`() {
        var loggedMessage: String? = null
        var loggedError: Throwable? = null
        SignInFailure.sink = { message, error ->
            loggedMessage = message
            loggedError = error
        }
        val error = IllegalStateException("boom")

        SignInFailure.log(error)

        assertTrue(loggedMessage!!.contains("IllegalStateException - boom"))
        assertEquals(error, loggedError)
    }
}
