package fr.cklla.pellicule.ui.navigation

import fr.cklla.pellicule.ui.AppTab

/**
 * Routes de navigation de l'app (Navigation Compose).
 *
 * Les 3 onglets (Bibliothèque/Recherche/Stats) sont des destinations de premier niveau, empilées
 * une seule fois grâce à `popUpTo`/`restoreState` dans `MainActivity`. D'autres routes (détail
 * d'un contenu, aperçu depuis la recherche...) viendront s'ajouter avec les écrans correspondants.
 */
object PelliculeDestinations {
    const val BIBLIOTHEQUE = "bibliotheque"
    const val RECHERCHE = "recherche"
    const val STATS = "stats"
}

/** Route associée à chaque onglet de la navigation basse. */
val AppTab.route: String
    get() = when (this) {
        AppTab.BIBLIOTHEQUE -> PelliculeDestinations.BIBLIOTHEQUE
        AppTab.RECHERCHE -> PelliculeDestinations.RECHERCHE
        AppTab.STATS -> PelliculeDestinations.STATS
    }
