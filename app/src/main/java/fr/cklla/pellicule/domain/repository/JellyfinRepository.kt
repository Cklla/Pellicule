package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.StateFlow

/**
 * Synchronisation bidirectionnelle du statut de visionnage avec un serveur Jellyfin
 * — module optionnel, `session` vaut `null` tant que
 * l'utilisateur n'a connecté aucun serveur.
 *
 * Le statut `WatchStatus` (à voir/en cours/vu) est dérivé de ce que renvoie Jellyfin, qui fait
 * foi côté lecture — voir [syncTrackedSeries] (épisodes) et [syncTrackedMovies] (statut de
 * lecture direct de l'item).
 */
interface JellyfinRepository {

    val session: StateFlow<JellyfinSession?>

    /** Authentifie l'utilisateur auprès du serveur et persiste la session si réussi. */
    suspend fun connect(serverUrl: String, username: String, password: String): Resource<Unit>

    fun disconnect()

    /**
     * Récupère depuis Jellyfin le statut vu des épisodes de chaque contenu SERIE/ANIME de [items],
     * réconcilie le suivi local des épisodes (Jellyfin fait foi côté lecture), et met à jour
     * `WatchStatus` en conséquence (aucun épisode vu → à voir, au moins un mais pas tous → en
     * cours, tous → vu). Résout et persiste au passage `Media.jellyfinId` pour les contenus pas
     * encore mappés. Ne fait rien si aucune session n'est active ; les erreurs par contenu
     * (résolution ou requête échouée) sont ignorées pour ne pas bloquer la synchro des autres.
     */
    suspend fun syncTrackedSeries(items: List<Media>)

    /**
     * Récupère depuis Jellyfin le statut de lecture de chaque contenu FILM de [items] (`UserData`
     * de l'item), et met à jour `WatchStatus` en conséquence (non entamé → à voir, position de
     * lecture > 0 mais pas marqué vu → en cours, marqué vu → vu). Résout et persiste au passage
     * `Media.jellyfinId`. Ne fait rien si aucune session n'est active ; une erreur réseau laisse le
     * statut local inchangé.
     */
    suspend fun syncTrackedMovies(items: List<Media>)

    /**
     * Pousse le statut vu d'un épisode vers Jellyfin, juste après une action locale. Best-effort :
     * ne fait rien si aucune session n'est active, ignore silencieusement un échec réseau (le
     * prochain [syncTrackedSeries] rattrapera l'écart).
     */
    suspend fun pushEpisodeWatched(media: Media, seasonNumber: Int, episodeNumber: Int, watched: Boolean)
}
