package fr.cklla.pellicule.data.remote.supabase

import fr.cklla.pellicule.data.remote.PermanentRemoteException
import fr.cklla.pellicule.data.remote.RemoteMediaDataSource
import fr.cklla.pellicule.data.remote.RemoteSnapshot
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Implémentation [RemoteMediaDataSource] sur Supabase (PostgREST pour les lectures et écritures,
 * Realtime pour le signal de changement). L'accès est limité au compte connecté par le RLS côté base :
 * aucune requête ne filtre sur `user_id`.
 */
class SupabaseMediaDataSource @Inject constructor(
    private val client: SupabaseClient,
) : RemoteMediaDataSource {

    // Aucun filtre sur `user_id` : Realtime n'applique pas un filtre aux suppressions (elles ne
    // portent que la clé primaire), et une suppression faite sur un autre appareil ne serait jamais
    // signalée. Les insertions et modifications restent limitées par le RLS aux lignes du compte ; une
    // suppression étrangère déclenche au pire une lecture inutile.
    override fun changes(): Flow<Unit> = channelFlow {
        val channel = client.channel(CHANNEL_NAME)
        val mediaChanges = channel.postgresChangeFlow<PostgresAction>(schema = SCHEMA) { table = TABLE_MEDIA }
        val episodeChanges = channel.postgresChangeFlow<PostgresAction>(schema = SCHEMA) { table = TABLE_EPISODE }
        launch { mediaChanges.collect { send(Unit) } }
        launch { episodeChanges.collect { send(Unit) } }
        // Les événements survenus pendant une coupure ne sont pas rejoués : chaque (re)abonnement
        // déclenche donc une lecture complète.
        launch { channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.collect { send(Unit) } }
        try {
            // Sans délai, un abonnement qui n'aboutit jamais (réseau absent, accès refusé) bloquerait
            // ici sans erreur, et la boucle de synchro ne réessaierait jamais.
            withTimeout(SUBSCRIBE_TIMEOUT_MILLIS) { channel.subscribe(blockUntilSubscribed = true) }
            // Le SDK abandonne un canal après plusieurs erreurs (jeton refusé…), ou le ferme à la
            // demande du serveur, en le repassant simplement à UNSUBSCRIBED. Le flux se termine alors
            // en erreur pour que l'appelant se réabonne, plutôt que de ne plus rien recevoir.
            channel.status.first { it == RealtimeChannel.Status.UNSUBSCRIBED }
            error("Écoute temps réel interrompue")
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    channel.unsubscribe()
                    client.realtime.removeChannel(channel)
                }
            }
        }
    }

    override suspend fun fetchAll(): RemoteSnapshot {
        val media = fetchAllPages(PAGE_SIZE) { from, to ->
            requireSession()
            client.from(TABLE_MEDIA).select {
                order("id", Order.ASCENDING)
                range(from, to)
            }.decodeList<MediaRow>()
        }
        val episodes = fetchAllPages(PAGE_SIZE) { from, to ->
            requireSession()
            client.from(TABLE_EPISODE).select {
                order("media_id", Order.ASCENDING)
                order("season", Order.ASCENDING)
                order("episode", Order.ASCENDING)
                range(from, to)
            }.decodeList<WatchedEpisodeRow>()
        }
        return RemoteSnapshot(
            media = media.mapNotNull { it.toMediaOrNull() },
            watchedEpisodes = episodes.groupBy({ it.mediaId }, { it.toEpisodeKey() }).mapValues { it.value.toSet() },
        )
    }

    // Sans `onConflict`, PostgREST fusionne sur la clé primaire (`id`) : rejouer l'envoi ne crée pas
    // de doublon.
    override suspend fun upsertMedia(media: Media) = write {
        client.from(TABLE_MEDIA).upsert(media.toRow())
    }

    override suspend fun deleteMedia(mediaId: String) = write {
        client.from(TABLE_MEDIA).delete { filter { eq("id", mediaId) } }
    }

    // `ignoreDuplicates` : « on conflict do nothing », un épisode déjà vu n'est pas une erreur.
    override suspend fun addWatchedEpisodes(mediaId: String, episodes: Set<EpisodeKey>) = write {
        episodes.sorted().chunked(WRITE_CHUNK_SIZE).forEach { chunk ->
            client.from(TABLE_EPISODE).upsert(chunk.map { it.toRow(mediaId) }) {
                onConflict = "media_id,season,episode"
                ignoreDuplicates = true
            }
        }
    }

    // Un seul filtre par saison (`episode in (...)`) plutôt qu'une condition par épisode : l'URL reste
    // courte même pour une saison de plusieurs centaines d'épisodes.
    override suspend fun removeWatchedEpisodes(mediaId: String, episodes: Set<EpisodeKey>) = write {
        episodes.groupBy({ it.seasonNumber }, { it.episodeNumber }).forEach { (season, numbers) ->
            numbers.sorted().chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
                client.from(TABLE_EPISODE).delete {
                    filter {
                        eq("media_id", mediaId)
                        eq("season", season)
                        isIn("episode", chunk)
                    }
                }
            }
        }
    }

    // Les erreurs de réseau ou de serveur remontent telles quelles (à réessayer) ; seul un refus
    // définitif du serveur est converti.
    private suspend fun write(block: suspend () -> Unit) {
        requireSession()
        try {
            block()
        } catch (e: PostgrestRestException) {
            if (isPermanentFailure(e.code)) {
                throw PermanentRemoteException("Écriture refusée (${e.statusCode}, ${e.code}) : ${e.description ?: e.error}", e)
            }
            throw e
        }
    }

    // Sans session, le SDK envoie la requête avec la seule clé publique : le RLS ne laisse alors voir
    // aucune ligne, sans erreur. Une lecture renverrait un suivi vide, que le miroir appliquerait en
    // supprimant tout le suivi local ; une suppression « réussirait » sans rien supprimer et sortirait
    // de la file. L'exception levée ici est une erreur ordinaire, réessayée plus tard.
    private fun requireSession() {
        checkNotNull(client.auth.currentSessionOrNull()) { "Aucune session : requête non envoyée" }
    }

    private companion object {
        const val CHANNEL_NAME = "pellicule-suivi"
        const val SCHEMA = "public"
        const val TABLE_MEDIA = "media"
        const val TABLE_EPISODE = "watched_episode"
        const val PAGE_SIZE = 1000
        const val WRITE_CHUNK_SIZE = 500
        const val DELETE_CHUNK_SIZE = 200
        const val SUBSCRIBE_TIMEOUT_MILLIS = 30_000L
    }
}
