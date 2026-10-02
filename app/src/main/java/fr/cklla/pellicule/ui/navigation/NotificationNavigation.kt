package fr.cklla.pellicule.ui.navigation

import fr.cklla.pellicule.notification.NotificationDestination
import fr.cklla.pellicule.notification.NotificationDestinationType

// Les identifiants de contenu sont des UUID : tout autre caractère (un extra forgé par une autre
// application, par exemple) ne doit pas pouvoir fabriquer une route arbitraire.
private val SAFE_ID = Regex("[A-Za-z0-9-]+")
private val YEAR = Regex("\\d{4}")

/** Route de navigation de la destination d'une notification, ou `null` si son identifiant est invalide. */
fun NotificationDestination.toRoute(): String? {
    if (!SAFE_ID.matches(id)) return null
    return when (type) {
        NotificationDestinationType.DETAIL -> PelliculeDestinations.detailRoute(id)
        NotificationDestinationType.RECAP -> if (YEAR.matches(id)) PelliculeDestinations.recapRoute(id.toInt()) else null
    }
}
