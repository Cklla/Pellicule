package fr.cklla.pellicule.notification

import android.content.Intent

/** Écran qu'une notification ouvre au tap. Un nouvel écran ciblable s'ajoute ici et dans `toRoute`. */
enum class NotificationDestinationType {
    /** Fiche Détail d'un contenu suivi ; l'identifiant est celui du contenu. */
    DETAIL,

    /** Récap annuel ; l'identifiant est l'année sur quatre chiffres. */
    RECAP,
}

/**
 * Destination d'une notification, transportée par deux extras d'`Intent` génériques (type +
 * identifiant) que `MainActivity` relit pour naviguer, quel que soit l'écran visé.
 */
data class NotificationDestination(val type: NotificationDestinationType, val id: String) {

    /** Ajoute les extras de cette destination à [intent] et le retourne. */
    fun putInto(intent: Intent): Intent = intent
        .putExtra(EXTRA_DESTINATION_TYPE, type.name)
        .putExtra(EXTRA_DESTINATION_ID, id)

    companion object {
        const val EXTRA_DESTINATION_TYPE = "notification_destination_type"
        const val EXTRA_DESTINATION_ID = "notification_destination_id"

        /** Destination portée par [intent], ou `null` s'il n'en porte pas (lancement normal) ou si elle est invalide. */
        fun fromIntent(intent: Intent?): NotificationDestination? {
            val type = intent?.getStringExtra(EXTRA_DESTINATION_TYPE)
                ?.let { name -> NotificationDestinationType.entries.firstOrNull { it.name == name } }
                ?: return null
            val id = intent.getStringExtra(EXTRA_DESTINATION_ID)?.takeIf { it.isNotBlank() } ?: return null
            return NotificationDestination(type, id)
        }
    }
}
