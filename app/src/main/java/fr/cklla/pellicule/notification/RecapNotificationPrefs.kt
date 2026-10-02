package fr.cklla.pellicule.notification

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.pellicule.domain.notification.RecapNotificationStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste, par année, l'envoi de la notification du récap : évite de renotifier chaque jour tant
 * que la fenêtre reste ouverte. Un booléen par année ne justifie pas DataStore, d'où de simples
 * `SharedPreferences`.
 */
@Singleton
class RecapNotificationPrefs @Inject constructor(@ApplicationContext context: Context) : RecapNotificationStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasNotified(year: Int): Boolean =
        prefs.getStringSet(KEY_NOTIFIED_YEARS, emptySet())?.contains(year.toString()) == true

    override fun markNotified(year: Int) {
        val updated = prefs.getStringSet(KEY_NOTIFIED_YEARS, emptySet()).orEmpty() + year.toString()
        prefs.edit { putStringSet(KEY_NOTIFIED_YEARS, updated) }
    }

    private companion object {
        const val PREFS_NAME = "recap_notification"
        const val KEY_NOTIFIED_YEARS = "notified_years"
    }
}
