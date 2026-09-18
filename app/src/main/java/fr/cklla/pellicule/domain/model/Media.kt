package fr.cklla.pellicule.domain.model

/**
 * Représente un contenu (film, série ou anime) suivi par l'utilisateur, tel que manipulé par
 * l'UI et les ViewModels.
 *
 * C'est le modèle "métier" : il ne dépend ni de Room (entité base de données) ni de Firestore, ni
 * de TMDB (réponse API). Le Repository fait la conversion entre ces représentations et ce modèle.
 *
 * @param id identifiant du contenu (UUID généré à la création) — chaîne vide si pas encore
 *   persisté. Sert aussi d'identifiant de document Firestore une fois la synchro cloud en place.
 * @param tmdbId identifiant TMDB du contenu, ou `null` si ajouté sans passer par la recherche.
 * @param jellyfinId identifiant de l'item correspondant sur le serveur Jellyfin connecté, résolu
 *   et mis en cache une fois trouvé (voir `JellyfinRepository`) — `null` tant que la résolution
 *   n'a pas eu lieu, ou si aucun serveur Jellyfin n'est connecté.
 * @param rating note personnelle de 1 à 5, ou `null` si le contenu n'est pas encore noté.
 * @param watchedAt horodatage (epoch millis) du passage au statut [WatchStatus.VU], `null` tant
 *   que ce statut n'a jamais été atteint. Distinct de [releaseYear] : sert à filtrer par année de
 *   *visionnage* plutôt que par année de sortie du contenu. Dérivé automatiquement par le
 *   Repository à chaque transition de statut (voir `MediaRepositoryImpl.resolveWatchedAt`),
 *   jamais renseigné à la main par l'UI.
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
)
