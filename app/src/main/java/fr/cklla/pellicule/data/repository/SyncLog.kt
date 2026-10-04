package fr.cklla.pellicule.data.repository

import android.util.Log

/**
 * Journal des échecs de synchro (logcat, balise `PelliculeSync`). Les erreurs réseau ou serveur y sont
 * avalées pour ne jamais gêner le suivi local ; sans trace, une synchro qui échoue à chaque lancement
 * reste invisible. Ne reçoit ni jeton ni mot de passe : seulement l'opération, le titre du contenu et
 * l'exception.
 */
internal object SyncLog {

    const val TAG = "PelliculeSync"

    /**
     * Destination des messages. `android.util.Log` n'existe pas dans un test JVM : l'appel y échoue, et
     * ce journal ne doit jamais faire échouer la synchro qu'il décrit.
     */
    var sink: (priority: Int, message: String, error: Throwable?) -> Unit = { priority, message, error ->
        runCatching { Log.println(priority, TAG, if (error == null) message else "$message : ${error.describe()}") }
    }

    fun info(message: String) = sink(Log.INFO, message, null)

    fun warn(message: String, error: Throwable) = sink(Log.WARN, message, error)

    private fun Throwable.describe() = "${this::class.java.simpleName}: $message"
}
