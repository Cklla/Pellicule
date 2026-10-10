package fr.cklla.pellicule.data.sync

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un identifiant de compte à retenir ne justifie pas DataStore : de simples `SharedPreferences`. Les
 * écritures sont synchrones (`commit`) : un marquage perdu à la mort du processus rejouerait l'envoi
 * initial au lancement suivant.
 */
@Singleton
class InitialUploadPrefs @Inject constructor(@ApplicationContext context: Context) : InitialUploadStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isDone(userId: String): Boolean = prefs.getString(KEY_USER_ID, null) == userId

    override fun markDone(userId: String) = prefs.edit(commit = true) { putString(KEY_USER_ID, userId) }

    override fun clear() = prefs.edit(commit = true) { remove(KEY_USER_ID) }

    private companion object {
        const val PREFS_NAME = "initial_upload"
        const val KEY_USER_ID = "user_id"
    }
}
