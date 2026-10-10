package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.local.OutboxDao
import fr.cklla.pellicule.data.local.TransactionRunner
import fr.cklla.pellicule.data.local.entity.MediaEntity
import fr.cklla.pellicule.data.remote.RemoteMediaDataSource
import fr.cklla.pellicule.data.sync.MediaSyncer
import fr.cklla.pellicule.data.sync.OutboxScheduler
import fr.cklla.pellicule.data.sync.enqueueDelete
import fr.cklla.pellicule.data.sync.enqueueUpsert
import fr.cklla.pellicule.di.ApplicationScope
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.domain.util.watchedAtForYear
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private sealed interface AuthEvent {
    data class UserChanged(val user: AuthUser?) : AuthEvent
    data object SignedOut : AuthEvent
}

/**
 * Implémentation Room + Supabase du [MediaRepository].
 *
 * Room reste l'unique source lue par l'UI (`observeMedia`/`observeMediaById`, UX offline-first, pas de
 * flicker si le serveur est indisponible). Toute écriture locale est enregistrée dans Room et dans la
 * file d'envoi (`pending_operation`) dans une même transaction, puis envoyée par [MediaSyncer] dès que
 * le réseau le permet. Le serveur est la source de vérité : l'écoute temps réel sert de signal pour
 * recopier son état dans Room, sans jamais toucher à ce qui attend d'être envoyé.
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val outboxDao: OutboxDao,
    private val transactions: TransactionRunner,
    private val remoteDataSource: RemoteMediaDataSource,
    private val syncer: MediaSyncer,
    private val outboxScheduler: OutboxScheduler,
    private val authRepository: AuthRepository,
    private val timeSource: TimeSource,
    @ApplicationScope private val repositoryScope: CoroutineScope,
) : MediaRepository {

    // Sérialise les lectures-puis-écritures de la ligne Room (`updateMedia`, `setWatchedYear`) : sans
    // ça, un changement d'année et une édition de note lancés coup sur coup pourraient lire le même
    // état, et la seconde écriture restaurerait l'ancien `watchedAt`.
    private val writeMutex = Mutex()

    init {
        // `collectLatest` : un changement d'utilisateur (déconnexion, ou reconnexion avec un autre
        // compte) annule proprement la synchro précédente avant d'en démarrer une nouvelle. La purge
        // passe par le même collecteur pour ne jamais tourner en même temps qu'un miroir en cours.
        // Elle n'est déclenchée que par la déconnexion demandée : un `currentUser` à `null` est aussi
        // l'état avant le chargement de la session, et effacerait Room à chaque lancement.
        repositoryScope.launch {
            merge(
                authRepository.currentUser.map { AuthEvent.UserChanged(it) },
                authRepository.signedOut.map { AuthEvent.SignedOut },
            ).collectLatest { event ->
                when (event) {
                    AuthEvent.SignedOut -> clearLocalData()
                    is AuthEvent.UserChanged -> if (event.user != null) syncWithServer()
                }
            }
        }
    }

    override fun observeMedia(): Flow<List<Media>> =
        mediaDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            // Un miroir serveur -> Room réémet souvent une liste identique (écho de l'écriture
            // locale elle-même) : éviter les recompositions inutiles.
            .distinctUntilChanged()

    override fun observeMediaById(id: String): Flow<Media?> =
        mediaDao.observeById(id).map { it?.toDomain() }

    // Le UUID est généré ici, avant la première écriture, plutôt que délégué à Room (qui ne sait
    // pas auto-générer un id texte) : Room et le serveur doivent partager exactement le même id, et
    // c'est lui qui rend le rejeu d'un envoi idempotent.
    override suspend fun addMedia(media: Media): Resource<String> = runCatching {
        val id = media.id.ifBlank { UUID.randomUUID().toString() }
        val mediaWithId = media.copy(id = id, watchedAt = resolveWatchedAt(previous = null, newStatus = media.status))
        transactions.run {
            mediaDao.insert(mediaWithId.toEntity())
            outboxDao.enqueueUpsert(id)
        }
        outboxScheduler.requestFlush()
        id
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter ce contenu au suivi.", it) },
    )

    override suspend fun updateMedia(media: Media): Resource<Unit> = runCatching {
        writeMutex.withLock {
            transactions.run {
                val previous = mediaDao.getByIdOnce(media.id)
                mediaDao.update(media.copy(watchedAt = resolveWatchedAt(previous, media.status)).toEntity())
                outboxDao.enqueueUpsert(media.id)
            }
        }
        outboxScheduler.requestFlush()
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour ce contenu.", it) },
    )

    override suspend fun setWatchedYear(mediaId: String, year: Int): Resource<Unit> = runCatching {
        val changed = writeMutex.withLock {
            transactions.run {
                val entity = mediaDao.getByIdOnce(mediaId)?.takeIf { it.status == WatchStatus.VU.name }
                    ?: return@run false
                val watchedAt = watchedAtForYear(year, entity.watchedAt, entity.releaseYear, timeSource)
                    ?.takeIf { it != entity.watchedAt }
                    ?: return@run false
                mediaDao.update(entity.copy(watchedAt = watchedAt))
                outboxDao.enqueueUpsert(mediaId)
                true
            }
        }
        if (changed) outboxScheduler.requestFlush()
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de modifier l'année de visionnage.", it) },
    )

    // Dérive automatiquement la date de visionnage à chaque transition de statut, plutôt que de
    // laisser l'UI la renseigner à la main : un seul point de vérité pour "quand un contenu est-il
    // devenu Vu", que le changement de statut vienne d'une sélection manuelle (DetailViewModel) ou
    // d'une synchro Jellyfin (JellyfinRepositoryImpl), les deux passant par `updateMedia`.
    //
    // Se base sur l'état déjà persisté en Room ([previous]), jamais sur `media.watchedAt` tel que
    // fourni par l'appelant : `DetailViewModel.workingMedia` ne relit jamais Room après son
    // chargement initial, un `watchedAt` calculé ici et jamais propagé en retour dans cette copie
    // de travail locale serait sinon écrasé par `null` au prochain appel.
    //
    // Horodatage posé une seule fois à l'entrée dans VU (pas de re-timestamp si déjà VU, sinon
    // éditer la note ou re-synchroniser Jellyfin déplacerait la date de visionnage à chaque fois,
    // et écraserait une année choisie à la main par `setWatchedYear`), effacé dès que le contenu
    // quitte VU (redevient pertinent le jour où il y repasse).
    private fun resolveWatchedAt(previous: MediaEntity?, newStatus: WatchStatus): Long? {
        val previousStatus = previous?.status?.let { runCatching { WatchStatus.valueOf(it) }.getOrNull() }
        return when {
            newStatus != WatchStatus.VU -> null
            previousStatus == WatchStatus.VU -> previous?.watchedAt
            else -> System.currentTimeMillis()
        }
    }

    override suspend fun deleteMedia(id: String): Resource<Unit> = runCatching {
        transactions.run {
            mediaDao.deleteById(id)
            outboxDao.enqueueDelete(id)
        }
        outboxScheduler.requestFlush()
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer ce contenu du suivi.", it) },
    )

    // Ne laisse rien du compte précédent sur l'appareil : ni la base Room, ni les écritures en
    // attente (elles partiraient sinon sur le compte suivant). Enveloppé dans un runCatching parce
    // qu'une exception ici (base verrouillée) annulerait le collecteur de `currentUser` et couperait
    // la synchro pour tout le reste de la vie du process.
    private suspend fun clearLocalData() {
        runCatching { syncer.clearLocalData() }
    }

    // Une erreur de lecture (réseau, serveur) ou de l'écoute temps réel termine le flux. Sans le
    // retry ci-dessous, la synchro s'arrêtait définitivement au premier incident, sans que rien ne le
    // signale. Le délai croît jusqu'à un plafond mais les tentatives ne s'arrêtent jamais : une app
    // lancée hors ligne doit se synchroniser dès que le réseau revient, sans relance.
    private suspend fun syncWithServer() {
        // Rejoue les écritures restées en attente d'un lancement précédent.
        outboxScheduler.requestFlush()
        flow {
            // Premier lancement du compte sur cet appareil : le suivi local que le serveur n'a pas part
            // dans la file d'envoi. Une lecture du serveur qui échoue lève ici, elle n'est jamais prise
            // pour un serveur vide.
            if (syncer.enqueueLocalOnFirstSync()) outboxScheduler.requestFlush()
            // Une première lecture sans attendre l'écoute temps réel : si celle-ci ne s'établit pas,
            // le suivi est tout de même recopié, puis relu à chaque nouvelle tentative.
            emit(Unit)
            emitAll(remoteDataSource.changes())
        }
            // Plusieurs signaux d'affilée (une rafale d'épisodes cochés) ne donnent qu'une lecture.
            .conflate()
            .onEach { mirrorUntilApplied() }
            .retryWhen { cause, attempt ->
                SyncLog.warn("Synchro interrompue, nouvelle tentative", cause)
                delay(minOf(RETRY_DELAY_MILLIS * (attempt + 1), MAX_RETRY_DELAY_MILLIS))
                true
            }
            .collect()
    }

    // Une lecture rendue périmée par un envoi qui se terminait n'est pas appliquée : on la refait
    // peu après plutôt que d'attendre le signal suivant, qui pourrait ne jamais venir.
    private suspend fun mirrorUntilApplied() {
        while (!syncer.mirror()) delay(STALE_RETRY_MILLIS)
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
        const val STALE_RETRY_MILLIS = 500L
    }
}
