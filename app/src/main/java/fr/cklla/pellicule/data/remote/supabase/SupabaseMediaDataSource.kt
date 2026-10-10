package fr.cklla.pellicule.data.remote.supabase

import fr.cklla.pellicule.data.remote.PermanentRemoteException
import fr.cklla.pellicule.data.remote.RemoteMediaDataSource
import fr.cklla.pellicule.data.remote.RemoteSnapshot
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        channel.subscribe(blockUntilSubscribed = true)
        try {
            awaitCancellation()
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
            client.from(TABLE_MEDIA).select {
                order("id", Order.ASCENDING)
                range(from, to)
            }.decodeList<MediaRow>()
        }
        val episodes = fetchAllPages(PAGE_SIZE) { from, to ->
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
        try {
            block()
        } catch (e: RestException) {
            if (isPermanentFailureStatus(e.statusCode)) {
                throw PermanentRemoteException("Écriture refusée (${e.statusCode}) : ${e.description ?: e.error}", e)
            }
            throw e
        }
    }

    private companion object {
        const val CHANNEL_NAME = "pellicule-suivi"
        const val SCHEMA = "public"
        const val TABLE_MEDIA = "media"
        const val TABLE_EPISODE = "watched_episode"
        const val PAGE_SIZE = 1000
        const val WRITE_CHUNK_SIZE = 500
        const val DELETE_CHUNK_SIZE = 200
    }
}
