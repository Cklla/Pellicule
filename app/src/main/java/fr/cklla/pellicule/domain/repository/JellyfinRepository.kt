package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.StateFlow

/**
 * Synchronisation bidirectionnelle du statut vu des épisodes avec un serveur Jellyfin (voir
 * CLAUDE.md, section Synchronisation) — module optionnel, `session` vaut `null` tant que
 * l'utilisateur n'a connecté aucun serveur.
 *
 * Limité aux épisodes de séries/anime (comme le suivi local, voir `EpisodeRepository`) : pas de
 * synchro pour les films dans cette itération.
 */
interface JellyfinRepository {

    val session: StateFlow<JellyfinSession?>

    /** Authentifie l'utilisateur auprès du serveur et persiste la session si réussi. */
    suspend fun connect(serverUrl: String, username: String, password: String): Resource<Unit>

    fun disconnect()

    /**
     * Récupère depuis Jellyfin le statut vu des épisodes de chaque contenu SERIE/ANIME de [items],
     * et réconcilie le suivi local (Jellyfin fait foi côté lecture). Résout et persiste au passage
     * `Media.jellyfinId` pour les contenus pas encore mappés. Ne fait rien si aucune session n'est
     * active ; les erreurs par contenu (résolution ou requête échouée) sont ignorées pour ne pas
     * bloquer la synchro des autres.
     */
    suspend fun syncTrackedSeries(items: List<Media>)

    /**
     * Pousse le statut vu d'un épisode vers Jellyfin, juste après une action locale. Best-effort :
     * ne fait rien si aucune session n'est active, ignore silencieusement un échec réseau (le
     * prochain [syncTrackedSeries] rattrapera l'écart).
     */
    suspend fun pushEpisodeWatched(media: Media, seasonNumber: Int, episodeNumber: Int, watched: Boolean)
}
