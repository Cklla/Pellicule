package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.local.entity.MediaEntity
import fr.cklla.pellicule.data.remote.firestore.FirestoreMediaDataSource
import fr.cklla.pellicule.di.ApplicationScope
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Implémentation Room + Firestore du [MediaRepository].
 *
 * Room reste l'unique source lue par l'UI (`observeMedia`/`observeMediaById`, UX offline-first,
 * pas de flicker si Firestore est temporairement indisponible). Firestore devient la source de
 * vérité dès que la synchro démarre : un listener temps réel mirrore en continu son contenu vers
 * Room, et les écritures locales sont répercutées vers Firestore en best-effort.
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val firestoreDataSource: FirestoreMediaDataSource,
    private val authRepository: AuthRepository,
    @ApplicationScope private val repositoryScope: CoroutineScope,
) : MediaRepository {

    init {
        // `collectLatest` : un changement d'utilisateur (déconnexion, ou reconnexion avec un autre
        // compte) annule proprement l'écoute Firestore précédente avant d'en démarrer une nouvelle.
        repositoryScope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user == null) {
                    clearLocalData()
                } else {
                    syncWith(user.uid)
                }
            }
        }
    }

    override fun observeMedia(): Flow<List<Media>> =
        mediaDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            // Un miroir Firestore -> Room réémet souvent une liste identique (écho de l'écriture
            // locale elle-même avant confirmation serveur) : éviter les recompositions inutiles.
            .distinctUntilChanged()

    override fun observeMediaById(id: String): Flow<Media?> =
        mediaDao.observeById(id).map { it?.toDomain() }

    // Le UUID est généré ici, avant la première écriture, plutôt que délégué à Room (qui ne sait
    // pas auto-générer un id texte) : c'est aussi le seul endroit qui écrit à la fois dans Room et
    // dans Firestore, les deux doivent donc partager exactement le même id.
    override suspend fun addMedia(media: Media): Resource<String> = runCatching {
        val id = media.id.ifBlank { UUID.randomUUID().toString() }
        val mediaWithId = media.copy(id = id, watchedAt = resolveWatchedAt(previous = null, newStatus = media.status))
        mediaDao.insert(mediaWithId.toEntity())
        pushToFirestore { uid -> firestoreDataSource.upsertMedia(uid, mediaWithId) }
        id
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter ce contenu au suivi.", it) },
    )

    override suspend fun updateMedia(media: Media): Resource<Unit> = runCatching {
        val previous = mediaDao.getByIdOnce(media.id)
        val mediaToPersist = media.copy(watchedAt = resolveWatchedAt(previous, media.status))
        mediaDao.update(mediaToPersist.toEntity())
        pushToFirestore { uid -> firestoreDataSource.upsertMedia(uid, mediaToPersist) }
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour ce contenu.", it) },
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
    // éditer la note ou re-synchroniser Jellyfin déplacerait la date de visionnage à chaque fois),
    // effacé dès que le contenu quitte VU (redevient pertinent le jour où il y repasse).
    private fun resolveWatchedAt(previous: MediaEntity?, newStatus: WatchStatus): Long? {
        val previousStatus = previous?.status?.let { runCatching { WatchStatus.valueOf(it) }.getOrNull() }
        return when {
            newStatus != WatchStatus.VU -> null
            previousStatus == WatchStatus.VU -> previous?.watchedAt
            else -> System.currentTimeMillis()
        }
    }

    override suspend fun deleteMedia(id: String): Resource<Unit> = runCatching {
        mediaDao.deleteById(id)
        pushToFirestore { uid -> firestoreDataSource.deleteMedia(uid, id) }
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer ce contenu du suivi.", it) },
    )

    // Ne laisse rien du compte précédent sur l'appareil : la base Room, mais aussi le cache que
    // le SDK Firestore tient de son côté. Enveloppé dans un runCatching parce qu'une exception
    // ici (base verrouillée, purge Firestore refusée) annulerait le collecteur de `currentUser`
    // et couperait la synchro pour tout le reste de la vie du process.
    private suspend fun clearLocalData() {
        runCatching {
            mediaDao.clearAll()
            firestoreDataSource.clearLocalCache()
        }
    }

    // Une erreur Firestore (réseau, règles de sécurité, quota) termine le flux d'écoute. Sans le
    // retry ci-dessous, la synchro s'arrêtait définitivement au premier incident, sans que rien
    // ne le signale : l'app continuait à tourner sur le seul contenu de Room, et il fallait la
    // relancer pour qu'elle se resynchronise.
    private suspend fun syncWith(uid: String) {
        bootstrapIfNeeded(uid)
        firestoreDataSource.observeMedia(uid)
            .onEach { media -> mirrorIntoRoom(media) }
            .retryWhen { _, attempt ->
                if (attempt >= MAX_SYNC_ATTEMPTS) {
                    false
                } else {
                    // Délai croissant : inutile de marteler Firestore si l'erreur est durable
                    // (pas de réseau, règles qui refusent l'accès...).
                    delay(RETRY_DELAY_MILLIS * (attempt + 1))
                    true
                }
            }
            .catch { }
            .collect()
    }

    // Upload automatique du suivi local existant, uniquement si Firestore n'a encore aucune
    // donnée pour cet utilisateur (première connexion). Si Firestore a déjà du contenu (connexion
    // déjà faite sur un autre appareil), on ne touche pas au local : le listener démarré juste
    // après va de toute façon remplacer le contenu Room par celui de Firestore.
    private suspend fun bootstrapIfNeeded(uid: String) {
        runCatching {
            val remoteMedia = firestoreDataSource.fetchMediaOnce(uid)
            if (remoteMedia.isEmpty()) {
                val localMedia = mediaDao.observeAll().first().map { it.toDomain() }
                if (localMedia.isNotEmpty()) {
                    firestoreDataSource.uploadAll(uid, localMedia)
                }
            }
        }
    }

    // Miroir complet : upsert du contenu distant + suppression des lignes Room absentes du
    // snapshot distant. Acceptable en performance vu la taille d'un suivi personnel. Les épisodes
    // vus (`watched_episode`) d'un contenu supprimé partent avec lui (`onDelete = CASCADE`).
    //
    // `mediaDao.upsert` (UPDATE si la ligne existe déjà), jamais `insert` (INSERT OR REPLACE) ici :
    // ce miroir tourne à chaque snapshot Firestore, y compris l'écho de nos propres écritures
    // locales (voir `observeMedia`) — avec `insert`, chaque écho referait un DELETE+INSERT de
    // *toutes* les lignes du snapshot et supprimerait en cascade les `watched_episode` de tous les
    // contenus suivis, pas seulement celui qui vient de changer.
    private suspend fun mirrorIntoRoom(remoteMedia: List<Media>) {
        val remoteIds = remoteMedia.map { it.id }.toSet()
        val localIds = mediaDao.getAllIds()
        localIds.filter { it !in remoteIds }.forEach { mediaDao.deleteById(it) }
        remoteMedia.forEach { mediaDao.upsert(it.toEntity()) }
    }

    // Écriture Firestore en best-effort : une erreur ici ne fait jamais échouer l'opération
    // locale — le SDK Firestore a une persistance offline native qui rejouera l'écriture toute
    // seule au retour du réseau. Pas d'écriture Firestore si personne n'est connecté (ne devrait
    // pas arriver, l'app gate tout accès aux écrans derrière la connexion, mais évite un appel
    // Firestore anonyme dans un cas limite).
    private suspend fun pushToFirestore(action: suspend (uid: String) -> Unit) {
        val uid = authRepository.currentUser.value?.uid ?: return
        runCatching { action(uid) }
    }

    private companion object {
        const val MAX_SYNC_ATTEMPTS = 5
        const val RETRY_DELAY_MILLIS = 2_000L
    }
}
