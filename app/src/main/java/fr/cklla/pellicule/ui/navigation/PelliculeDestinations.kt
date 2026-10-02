package fr.cklla.pellicule.ui.navigation

import android.net.Uri
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.ui.AppTab

/**
 * Routes de navigation de l'app (Navigation Compose).
 *
 * Les 3 onglets (Bibliothèque/Recherche/Compte) sont des destinations de premier niveau, empilées
 * une seule fois grâce à `popUpTo`/`restoreState` dans `MainActivity`. Détail est poussé par-dessus
 * depuis la Bibliothèque et n'a pas de barre de navigation basse.
 */
object PelliculeDestinations {
    const val BIBLIOTHEQUE = "bibliotheque"
    const val RECHERCHE = "recherche"
    const val COMPTE = "compte"
    const val JELLYFIN_SETTINGS = "jellyfin-settings"
    const val STATISTIQUES = "statistiques"

    // Récap annuel : l'année est portée par la route, et la liste des contenus vus sous-route de
    // l'écran du récap. Le type vaut `RECAP_MEDIA_TYPE_ALL` pour tous les types, sinon un nom de MediaType.
    const val RECAP_ARG_YEAR = "year"
    const val RECAP_ARG_TYPE = "type"
    const val RECAP_MEDIA_TYPE_ALL = "TOUS"
    const val RECAP = "recap/{$RECAP_ARG_YEAR}"
    const val RECAP_MEDIA = "recap/{$RECAP_ARG_YEAR}/media/{$RECAP_ARG_TYPE}"

    fun recapRoute(year: Int) = "recap/$year"

    fun recapMediaRoute(year: Int, type: MediaType?) = "recap/$year/media/${type?.name ?: RECAP_MEDIA_TYPE_ALL}"

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
        AppTab.COMPTE -> PelliculeDestinations.COMPTE
    }
