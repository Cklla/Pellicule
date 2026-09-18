package fr.cklla.pellicule.ui.navigation

import fr.cklla.pellicule.ui.AppTab

/**
 * Routes de navigation de l'app (Navigation Compose).
 *
 * Les 3 onglets (Bibliothèque/Recherche/Stats) sont des destinations de premier niveau, empilées
 * une seule fois grâce à `popUpTo`/`restoreState` dans `MainActivity`. Détail est poussé par-dessus
 * depuis la Bibliothèque et n'a pas de barre de navigation basse.
 */
object PelliculeDestinations {
    const val BIBLIOTHEQUE = "bibliotheque"
    const val RECHERCHE = "recherche"
    const val STATS = "stats"

    const val DETAIL_ARG_MEDIA_ID = "mediaId"
    const val DETAIL = "detail/{$DETAIL_ARG_MEDIA_ID}"

    fun detailRoute(mediaId: String) = "detail/$mediaId"
}

/** Route associée à chaque onglet de la navigation basse. */
val AppTab.route: String
    get() = when (this) {
        AppTab.BIBLIOTHEQUE -> PelliculeDestinations.BIBLIOTHEQUE
        AppTab.RECHERCHE -> PelliculeDestinations.RECHERCHE
        AppTab.STATS -> PelliculeDestinations.STATS
    }
