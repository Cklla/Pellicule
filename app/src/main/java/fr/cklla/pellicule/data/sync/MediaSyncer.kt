package fr.cklla.pellicule.data.sync

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.local.OutboxDao
import fr.cklla.pellicule.data.local.TransactionRunner
import fr.cklla.pellicule.data.local.entity.PendingOperationEntity
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import fr.cklla.pellicule.data.remote.PermanentRemoteException
import fr.cklla.pellicule.data.remote.RemoteMediaDataSource
import fr.cklla.pellicule.data.remote.RemoteSnapshot
import fr.cklla.pellicule.data.repository.SyncLog
import fr.cklla.pellicule.data.repository.toDomain
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

enum class FlushResult {
    /** La file est vide. */
    DONE,

    /** Il reste des opérations, à réessayer plus tard (réseau, serveur, session pas prête). */
    RETRY_LATER,
}

/**
 * Cœur de la synchro avec le serveur : envoi des écritures en attente ([flush]) et recopie de l'état du
 * serveur dans Room ([mirror]). Les deux partagent un état, d'où une seule classe.
 *
 * Deux règles évitent qu'un état serveur périmé n'écrase ou ne supprime une écriture locale :
 *  - le miroir ne touche jamais un contenu ni un épisode ayant une opération en attente ;
 *  - un état lu avant le dernier envoi réussi est jeté (compteur de génération). Sans cela, un état
 *    lu juste avant qu'une création ne parte, appliqué juste après la sortie de l'opération de la
 *    file, supprimerait la ligne qui vient d'être créée.
 */
@Singleton
class MediaSyncer @Inject constructor(
    private val remote: RemoteMediaDataSource,
    private val outboxDao: OutboxDao,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val transactions: TransactionRunner,
    private val authRepository: AuthRepository,
    private val initialUpload: InitialUploadStore,
) {

    // Protège le compteur de génération et rend atomiques, vis-à-vis du miroir, la sortie d'une
    // opération de la file et l'incrément du compteur. L'envoi réseau lui-même se fait hors verrou.
    private val stateLock = Mutex()
    private var generation = 0L

    // Un seul envoi à la fois : deux envois simultanés rejoueraient les mêmes opérations.
    private val flushLock = Mutex()

    /**
     * Envoie les opérations en attente dans l'ordre. Une opération ne quitte la file qu'une fois
     * confirmée par le serveur ; sur une erreur réseau ou serveur, l'envoi s'arrête et la file est
     * conservée telle quelle. Une opération que le serveur refuse définitivement est abandonnée (et
     * journalisée), sans quoi elle bloquerait toutes les suivantes.
     */
    suspend fun flush(): FlushResult = flushLock.withLock { flushQueue() }

    private suspend fun flushQueue(): FlushResult {
        while (true) {
            val operation = outboxDao.first() ?: return FlushResult.DONE
            // Sans compte connecté, le serveur refuserait l'écriture : elle serait prise à tort pour
            // un refus définitif.
            if (!awaitSignedIn()) return FlushResult.RETRY_LATER
            when (send(operation)) {
                SendOutcome.FAILED -> return FlushResult.RETRY_LATER
                SendOutcome.SENT, SendOutcome.REJECTED -> complete(operation)
            }
        }
    }

    /**
     * Recopie l'état du serveur dans Room. Renvoie `false` si, malgré plusieurs tentatives, chaque
     * lecture a été rendue périmée par un envoi qui se terminait : l'appelant réessaie un peu plus
     * tard. Une lecture qui échoue lève son exception.
     */
    suspend fun mirror(): Boolean {
        repeat(MAX_MIRROR_ATTEMPTS) {
            val startGeneration = stateLock.withLock { generation }
            val snapshot = remote.fetchAll()
            val applied = stateLock.withLock {
                if (generation != startGeneration) {
                    false
                } else {
                    transactions.run { applySnapshot(snapshot) }
                    true
                }
            }
            if (applied) return true
        }
        return false
    }

    /**
     * Au premier lancement d'un compte sur cet appareil, met dans la file d'envoi ce que le suivi local
     * a de plus que le serveur : les contenus qu'il n'a pas, puis les épisodes vus qu'il n'a pas (y
     * compris pour un contenu qu'il a déjà). Le serveur n'est jamais écrasé, seulement complété. Passer
     * par la file protège ces lignes du miroir tant qu'elles ne sont pas parties, et un échec réseau se
     * rejoue.
     *
     * N'a lieu qu'une fois par compte et par appareil ([InitialUploadStore]) : ensuite le serveur fait
     * foi, et un contenu supprimé ailleurs n'est plus renvoyé depuis un Room resté en retard. Une
     * lecture du serveur qui échoue lève son exception, sans marquer l'envoi comme fait : elle n'est
     * jamais prise pour un serveur vide. Renvoie `true` si la file a été remplie.
     *
     * Appelée à chaque démarrage de la synchro, donc aussi à chaque nouvelle tentative tant que le
     * marquage n'est pas posé : ce qui attend déjà dans la file n'est pas remis en double.
     */
    suspend fun enqueueLocalOnFirstSync(): Boolean {
        val userId = authRepository.currentUser.value?.uid ?: return false
        if (initialUpload.isDone(userId)) return false
        val snapshot = remote.fetchAll()
        val queued = stateLock.withLock {
            transactions.run {
                val pending = outboxDao.getAll()
                val pendingUpserts = pending
                    .filter { it.operationKind == PendingOperationKind.UPSERT_MEDIA }
                    .mapTo(mutableSetOf()) { it.mediaId }
                val pendingAdds = pending
                    .filter { it.operationKind == PendingOperationKind.ADD_EPISODES }
                    .groupBy({ it.mediaId }, { it.episodeKeys() })
                    .mapValues { (_, keys) -> keys.flatten().toSet() }

                val remoteIds = snapshot.media.mapTo(mutableSetOf()) { it.id }
                val mediaToSend = mediaDao.getAllOnce().map { it.id }
                    .filter { it !in remoteIds && it !in pendingUpserts }
                mediaToSend.forEach { outboxDao.enqueueUpsert(it) }
                val episodesToSend = episodeDao.getAllWatchedOnce()
                    .groupBy({ it.mediaId }, { EpisodeKey(it.seasonNumber, it.episodeNumber) })
                    .mapValues { (mediaId, keys) ->
                        keys.toSet() - snapshot.watchedEpisodes[mediaId].orEmpty() - pendingAdds[mediaId].orEmpty()
                    }
                    .filterValues { it.isNotEmpty() }
                episodesToSend.forEach { (mediaId, keys) ->
                    outboxDao.enqueueEpisodes(PendingOperationKind.ADD_EPISODES, mediaId, keys)
                }
                mediaToSend.isNotEmpty() || episodesToSend.isNotEmpty()
            }
        }
        // Après la validation de la transaction : un arrêt entre les deux rejoue la mise en file, qui
        // ne double rien.
        initialUpload.markDone(userId)
        return queued
    }

    /** Efface le suivi local et la file d'envoi : rien du compte qui se déconnecte ne doit rester. */
    suspend fun clearLocalData() {
        stateLock.withLock {
            generation++
            transactions.run {
                mediaDao.clearAll()
                outboxDao.clearAll()
            }
            initialUpload.clear()
        }
    }

    private enum class SendOutcome { SENT, REJECTED, FAILED }

    private suspend fun send(operation: PendingOperationEntity): SendOutcome = try {
        execute(operation)
        SendOutcome.SENT
    } catch (e: CancellationException) {
        throw e
    } catch (e: PermanentRemoteException) {
        SyncLog.warn("Écriture abandonnée, refusée par le serveur (${operation.kind}, contenu ${operation.mediaId})", e)
        SendOutcome.REJECTED
    } catch (e: Exception) {
        SyncLog.warn("Envoi impossible pour l'instant (${operation.kind}, contenu ${operation.mediaId})", e)
        SendOutcome.FAILED
    }

    private suspend fun execute(operation: PendingOperationEntity) {
        when (operation.operationKind) {
            // L'état envoyé est relu maintenant, pas celui du moment de l'écriture : toujours le plus
            // récent. Une ligne disparue depuis n'a rien à envoyer (sa suppression est en file).
            PendingOperationKind.UPSERT_MEDIA ->
                mediaDao.getByIdOnce(operation.mediaId)?.let { remote.upsertMedia(it.toDomain()) }
            PendingOperationKind.DELETE_MEDIA -> remote.deleteMedia(operation.mediaId)
            PendingOperationKind.ADD_EPISODES -> remote.addWatchedEpisodes(operation.mediaId, operation.episodeKeys())
            PendingOperationKind.REMOVE_EPISODES -> remote.removeWatchedEpisodes(operation.mediaId, operation.episodeKeys())
            null -> SyncLog.warn("Opération inconnue ignorée (${operation.kind})", IllegalStateException(operation.kind))
        }
    }

    // L'incrément précède le retrait de la file : un miroir qui voit l'ancienne génération voit donc
    // aussi l'opération encore en file (les deux se font sous le même verrou que l'application du
    // miroir). Non annulable : annulé entre les deux, l'envoi serait rejoué, mais jamais perdu.
    private suspend fun complete(operation: PendingOperationEntity) {
        withContext(NonCancellable) {
            stateLock.withLock {
                generation++
                outboxDao.delete(operation.seq)
            }
        }
    }

    private suspend fun awaitSignedIn(): Boolean {
        val state = withTimeoutOrNull(AUTH_WAIT_MILLIS) {
            authRepository.authState.first { it !is AuthState.Initializing }
        }
        return state is AuthState.SignedIn
    }

    // Appelé sous `stateLock`, dans une transaction Room.
    private suspend fun applySnapshot(snapshot: RemoteSnapshot) {
        val pending = outboxDao.getAll()
        val pendingMedia = pending
            .filter { it.operationKind == PendingOperationKind.UPSERT_MEDIA || it.operationKind == PendingOperationKind.DELETE_MEDIA }
            .mapTo(mutableSetOf()) { it.mediaId }
        val pendingEpisodes = mutableMapOf<String, MutableSet<EpisodeKey>>()
        pending
            .filter { it.operationKind == PendingOperationKind.ADD_EPISODES || it.operationKind == PendingOperationKind.REMOVE_EPISODES }
            .forEach { pendingEpisodes.getOrPut(it.mediaId) { mutableSetOf() } += it.episodeKeys() }

        val remoteIds = snapshot.media.mapTo(mutableSetOf()) { it.id }
        val localById = mediaDao.getAllOnce().associateBy { it.id }

        // Un contenu absent du serveur est supprimé en local, sauf s'il attend d'y être créé (ou déjà
        // supprimé, auquel cas il n'est plus là). Ses épisodes partent avec lui (clé étrangère) ; les
        // opérations d'épisodes en attente qui le visaient n'ont plus d'objet.
        localById.keys.filter { it !in remoteIds && it !in pendingMedia }.forEach { id ->
            mediaDao.deleteById(id)
            outboxDao.deleteForMedia(id)
        }

        // `upsert` (UPDATE si la ligne existe), jamais `insert` (INSERT OR REPLACE) : un remplacement
        // supprimerait en cascade les épisodes vus du contenu. N'écrit que ce qui diffère, pour ne pas
        // relancer les observateurs à chaque écho.
        snapshot.media.filter { it.id !in pendingMedia }.forEach { media ->
            val entity = media.toEntity()
            if (localById[media.id] != entity) mediaDao.upsert(entity)
        }

        val existingIds = mediaDao.getAllIds().toSet()
        val localEpisodes = episodeDao.getAllWatchedOnce()
            .groupBy({ it.mediaId }, { EpisodeKey(it.seasonNumber, it.episodeNumber) })
        snapshot.media.filter { it.id in existingIds }.forEach { media ->
            val local = localEpisodes[media.id].orEmpty().toSet()
            val inFlight = pendingEpisodes[media.id].orEmpty()
            // Le serveur fait foi, sauf pour un épisode dont le changement attend d'être envoyé :
            // celui-là garde son état local.
            val desired = (snapshot.watchedEpisodes[media.id].orEmpty() - inFlight) + (local intersect inFlight)
            if (desired != local) {
                episodeDao.replaceAllForMedia(
                    media.id,
                    desired.map { WatchedEpisodeEntity(media.id, it.seasonNumber, it.episodeNumber) },
                )
            }
        }
    }

    private companion object {
        const val MAX_MIRROR_ATTEMPTS = 3
        const val AUTH_WAIT_MILLIS = 10_000L
    }
}
