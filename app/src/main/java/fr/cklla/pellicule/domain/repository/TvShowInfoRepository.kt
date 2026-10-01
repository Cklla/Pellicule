package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.TvShowInfo
import kotlinx.coroutines.flow.Flow

/**
 * Métadonnées TMDB des séries/anime suivis, conservées dans un cache local (Room) pour calculer le
 * prochain épisode sans appel réseau à chaque affichage. Contrairement à [TvDetailsRepository], qui
 * interroge TMDB à chaque ouverture de la fiche Détail, ce cache ne sert qu'à la Bibliothèque et au
 * recalcul du statut.
 *
 * Les lectures ne font jamais d'appel réseau : seuls [refreshIfStale] et [refreshEpisodesIfStale]
 * interrogent TMDB, et seulement quand la donnée est absente ou périmée (24 h pour une série en
 * cours de diffusion, 30 jours pour une série terminée ou annulée). Un cache périmé reste lisible
 * hors ligne.
 */
interface TvShowInfoRepository {

    /** Toutes les séries en cache, indexées par `tmdbId`, mises à jour à chaque rafraîchissement. */
    fun observeShows(): Flow<Map<Long, TvShowInfo>>

    /** La série en cache (même périmée), ou `null` si elle n'a jamais été chargée. Sans réseau. */
    suspend fun getCachedShow(tmdbId: Long): TvShowInfo?

    /** Recharge la fiche de la série (`GET /tv/{id}`) si elle est absente ou périmée. Silencieux en cas d'échec. */
    suspend fun refreshIfStale(tmdbId: Long)

    /**
     * Recharge titres et dates des épisodes d'une saison (`GET /tv/{id}/season/{n}`) si elle n'a
     * jamais été chargée ou si son détail est périmé. Ne fait rien tant que la série n'est pas en
     * cache. Silencieux en cas d'échec.
     */
    suspend fun refreshEpisodesIfStale(tmdbId: Long, seasonNumber: Int)
}
