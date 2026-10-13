package fr.cklla.pellicule.data.repository

import android.util.Log
import androidx.credentials.exceptions.GetCredentialException
import io.github.jan.supabase.exceptions.RestException

/**
 * Diagnostic d'un échec de connexion Google : sans lui, tout échec se réduit au même message
 * générique, impossible à départager entre un refus de Supabase, un Play Services défaillant ou une
 * configuration OAuth incomplète.
 *
 * En release, R8 renomme les classes d'exception : le nom de classe seul ne dirait rien. On s'appuie
 * donc sur les identifiants stables (type Credential Manager, erreur et code HTTP de Supabase) puis sur le
 * message. Ni jeton ni identifiant de compte n'y figurent.
 */
internal object SignInFailure {

    const val TAG = "PelliculeAuth"

    private const val MAX_MESSAGE_LENGTH = 120

    /**
     * Destination du journal. `android.util.Log` n'existe pas dans un test JVM : l'appel y échoue, et
     * ce journal ne doit jamais faire échouer la connexion qu'il décrit.
     */
    var sink: (message: String, error: Throwable) -> Unit = { message, error ->
        runCatching { Log.e(TAG, message, error) }
    }

    fun log(error: Throwable) = sink("Connexion Google en échec : ${describe(error)}", error)

    /** Court libellé affichable à l'utilisateur, à lui faire transmettre en cas de problème. */
    fun describe(error: Throwable): String {
        val code = when (error) {
            is GetCredentialException -> error.type.substringAfterLast('.')
            is RestException -> "${error.error}/${error.response.status.value}"
            else -> error::class.java.simpleName
        }
        val detail = error.message?.takeIf { it.isNotBlank() }?.take(MAX_MESSAGE_LENGTH)
        return if (detail == null) code else "$code - $detail"
    }
}
