package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Faux serveur en mémoire, utilisé pour tester la synchro sans SDK réseau. Un seul compte simulé.
 *
 * [enforceForeignKey] reproduit la clé étrangère de `watched_episode` : un épisode ne s'ajoute que sur un
 * contenu existant. Les tests de ViewModel, qui cochent des épisodes sans créer le contenu sur le
 * serveur, la désactivent pour ne pas semer de refus dans le journal de synchro.
 */
class FakeRemoteMediaDataSource(private val enforceForeignKey: Boolean = true) : RemoteMediaDataSource {

    val media = MutableStateFlow<List<Media>>(emptyList())

    /** Épisodes vus par contenu. */
    val episodes = MutableStateFlow<Map<String, Set<EpisodeKey>>>(emptyMap())

    /** Coupure réseau : toute opération lève une [IOException]. */
    var offline = false

    /** Exception levée par chaque écriture (ex. un [PermanentRemoteException]), tant qu'elle est posée. */
    var writeFailure: Throwable? = null

    /** Nombre d'écritures acceptées avant que le réseau ne tombe ; `null` : pas de limite. */
    var writesBeforeFailure: Int? = null

    /** Nombre d'écoutes qui échouent avant de se comporter normalement. */
    var failedChangesAttempts = 0

    /** Exécuté après la lecture de l'état mais avant de le renvoyer : simule un envoi pendant la lecture. */
    var onFetch: (suspend () -> Unit)? = null

    var changesCallCount = 0
        private set
    var fetchCount = 0
        private set

    val upsertedMedia = mutableListOf<Media>()
    val deletedMediaIds = mutableListOf<String>()
    val addedEpisodes = mutableListOf<Pair<String, Set<EpisodeKey>>>()
    val removedEpisodes = mutableListOf<Pair<String, Set<EpisodeKey>>>()

    /** Ordre global des écritures reçues, pour vérifier qu'un contenu précède ses épisodes. */
    val writeLog = mutableListOf<String>()

    override fun changes(): Flow<Unit> = flow {
        changesCallCount++
        if (changesCallCount <= failedChangesAttempts) error("Échec d'écoute simulé")
        checkOnline()
        emit(Unit)
        emitAll(combine(media, episodes) { _, _ -> }.drop(1))
    }

    override suspend fun fetchAll(): RemoteSnapshot {
        checkOnline()
        fetchCount++
        val snapshot = RemoteSnapshot(media.value, episodes.value)
        onFetch?.invoke()
        return snapshot
    }

    override suspend fun upsertMedia(media: Media) {
        checkWritable()
        upsertedMedia += media
        writeLog += "upsert:${media.id}"
        this.media.update { list -> list.filterNot { it.id == media.id } + media }
    }

    override suspend fun deleteMedia(mediaId: String) {
        checkWritable()
        deletedMediaIds += mediaId
        writeLog += "delete:$mediaId"
        media.update { list -> list.filterNot { it.id == mediaId } }
        episodes.update { it - mediaId }
    }

    override suspend fun addWatchedEpisodes(mediaId: String, episodes: Set<EpisodeKey>) {
        checkWritable()
        requireMedia(mediaId)
        addedEpisodes += mediaId to episodes
        writeLog += "add:$mediaId"
        this.episodes.update { it + (mediaId to (it[mediaId].orEmpty() + episodes)) }
    }

    override suspend fun removeWatchedEpisodes(mediaId: String, episodes: Set<EpisodeKey>) {
        checkWritable()
        removedEpisodes += mediaId to episodes
        writeLog += "remove:$mediaId"
        this.episodes.update { it + (mediaId to (it[mediaId].orEmpty() - episodes)) }
    }

    private fun requireMedia(mediaId: String) {
        if (enforceForeignKey && media.value.none { it.id == mediaId }) {
            throw PermanentRemoteException("Clé étrangère : le contenu $mediaId n'existe pas sur le serveur")
        }
    }

    private fun checkOnline() {
        if (offline) throw IOException("Réseau coupé (simulé)")
    }

    private fun checkWritable() {
        checkOnline()
        writeFailure?.let { throw it }
        writesBeforeFailure?.let { remaining ->
            if (remaining <= 0) throw IOException("Réseau coupé en cours d'envoi (simulé)")
            writesBeforeFailure = remaining - 1
        }
    }
}
