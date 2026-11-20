package fr.cklla.pellicule.domain.model

/**
 * Représente un contenu (film, série ou anime) suivi par l'utilisateur, tel que manipulé par
 * l'UI et les ViewModels.
 *
 * C'est le modèle "métier" : il ne dépend ni de Room (entité base de données) ni du serveur, ni
 * de TMDB (réponse API). Le Repository fait la conversion entre ces représentations et ce modèle.
 *
 * @param id identifiant du contenu (UUID généré à la création) — chaîne vide si pas encore
 *   persisté. Sert aussi d'identifiant de la ligne côté serveur.
 * @param tmdbId identifiant TMDB du contenu, ou `null` si ajouté sans passer par la recherche.
 * @param jellyfinId identifiant de l'item correspondant sur le serveur Jellyfin connecté, résolu
 *   et mis en cache une fois trouvé (voir `JellyfinRepository`) — `null` tant que la résolution
 *   n'a pas eu lieu, ou si aucun serveur Jellyfin n'est connecté.
 * @param rating note personnelle de 1 à 5, ou `null` si le contenu n'est pas encore noté.
 * @param watchedAt horodatage (epoch millis) du passage au statut [WatchStatus.VU], `null` tant
 *   que ce statut n'a jamais été atteint. Distinct de [releaseYear] : sert à filtrer par année de
 *   *visionnage* plutôt que par année de sortie du contenu. Dérivé automatiquement par le
 *   Repository à chaque transition de statut (voir `MediaRepositoryImpl.resolveWatchedAt`) ; seule
 *   exception, l'année peut être corrigée à la main via `MediaRepository.setWatchedYear`, tant que
 *   le contenu reste au statut Vu. Effacé dès que le statut quitte [WatchStatus.VU].
 * @param notes notes libres de l'utilisateur sur ce contenu (impressions, où il s'est arrêté…), chaîne
 *   vide s'il n'en a pas écrit. Bornées à [MAX_NOTES_LENGTH] caractères, limite que le serveur impose aussi.
 */
data class Media(
    val id: String = "",
    val title: String,
    val type: MediaType,
    val status: WatchStatus,
    val tmdbId: Long? = null,
    val releaseYear: Int? = null,
    val posterUrl: String? = null,
    val jellyfinId: String? = null,
    val rating: Int? = null,
    val watchedAt: Long? = null,
    val notes: String = "",
)

/** Longueur maximale des notes libres, identique à la contrainte `CHECK` de la colonne `notes` côté serveur. */
const val MAX_NOTES_LENGTH = 5000
