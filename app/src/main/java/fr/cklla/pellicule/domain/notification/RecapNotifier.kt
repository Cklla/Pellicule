package fr.cklla.pellicule.domain.notification

/** Affiche la notification du récap annuel. */
interface RecapNotifier {
    /** Retourne `false` si la notification n'a pas pu être affichée (permission ou réglage refusé). */
    fun notify(year: Int): Boolean
}

/** Mémorise, par année, si la notification du récap a déjà été envoyée. */
interface RecapNotificationStore {
    fun hasNotified(year: Int): Boolean
    fun markNotified(year: Int)
}
