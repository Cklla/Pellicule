package fr.cklla.pellicule.domain.model

/** Une plateforme proposant un contenu en France (données JustWatch, servies par TMDB). */
data class WatchProvider(
    val id: Int,
    val name: String,
    val logoUrl: String?,
)

/**
 * Disponibilité d'un contenu en France.
 *
 * [Unknown] et un [Known] aux deux listes vides sont deux situations différentes : la première
 * signifie qu'on ignore la disponibilité (aucune donnée pour ce contenu, ou appel en échec), la
 * seconde qu'il n'existe aucune offre française alors que la donnée, elle, existe bien.
 */
sealed interface WatchAvailability {

    data object Unknown : WatchAvailability

    data class Known(
        /** Abonnement, gratuit ou financé par la publicité — regroupés : dans tous les cas on regarde sans acheter. */
        val streaming: List<WatchProvider>,
        val rentOrBuy: List<WatchProvider>,
        /** Page JustWatch du contenu pour la France, à créditer à côté des plateformes. */
        val link: String?,
    ) : WatchAvailability
}
