package fr.cklla.pellicule.ui.navigation

import android.net.Uri
import fr.cklla.pellicule.domain.model.MediaSearchResult
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
    const val JELLYFIN_SETTINGS = "jellyfin-settings"

    const val DETAIL_ARG_MEDIA_ID = "mediaId"
    const val DETAIL = "detail/{$DETAIL_ARG_MEDIA_ID}"

    fun detailRoute(mediaId: String) = "detail/$mediaId"

    // Fiche d'un contenu pas encore ajouté au suivi, ouverte directement depuis un résultat de
    // Recherche : il n'y a pas encore d'id local à relire en base, donc le résultat TMDB transite
    // par les paramètres de la route plutôt que par un id. Route distincte de DETAIL ci-dessus
    // (préfixe différent) pour qu'il n'y ait jamais d'ambiguïté de correspondance entre les deux
    // patterns dans le graphe de navigation.
    const val DETAIL_APERCU_ARG_TMDB_ID = "tmdbId"
    const val DETAIL_APERCU_ARG_TITLE = "title"
    const val DETAIL_APERCU_ARG_TYPE = "type"
    const val DETAIL_APERCU_ARG_YEAR = "year"
    const val DETAIL_APERCU_ARG_POSTER_URL = "posterUrl"

    private const val DETAIL_APERCU_BASE = "apercu-media"
    const val DETAIL_APERCU = "$DETAIL_APERCU_BASE?" +
        "$DETAIL_APERCU_ARG_TMDB_ID={$DETAIL_APERCU_ARG_TMDB_ID}" +
        "&$DETAIL_APERCU_ARG_TITLE={$DETAIL_APERCU_ARG_TITLE}" +
        "&$DETAIL_APERCU_ARG_TYPE={$DETAIL_APERCU_ARG_TYPE}" +
        "&$DETAIL_APERCU_ARG_YEAR={$DETAIL_APERCU_ARG_YEAR}" +
        "&$DETAIL_APERCU_ARG_POSTER_URL={$DETAIL_APERCU_ARG_POSTER_URL}"

    fun detailApercuRoute(result: MediaSearchResult): String {
        fun enc(value: String) = Uri.encode(value)
        return "$DETAIL_APERCU_BASE?" +
            "$DETAIL_APERCU_ARG_TMDB_ID=${result.tmdbId}" +
            "&$DETAIL_APERCU_ARG_TITLE=${enc(result.title)}" +
            "&$DETAIL_APERCU_ARG_TYPE=${result.type.name}" +
            "&$DETAIL_APERCU_ARG_YEAR=${result.year ?: ""}" +
            "&$DETAIL_APERCU_ARG_POSTER_URL=${enc(result.posterUrl.orEmpty())}"
    }
}

/** Route associée à chaque onglet de la navigation basse. */
val AppTab.route: String
    get() = when (this) {
        AppTab.BIBLIOTHEQUE -> PelliculeDestinations.BIBLIOTHEQUE
        AppTab.RECHERCHE -> PelliculeDestinations.RECHERCHE
        AppTab.STATS -> PelliculeDestinations.STATS
    }
