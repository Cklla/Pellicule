package fr.cklla.pellicule.data.remote.firestore

import fr.cklla.pellicule.domain.model.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Faux data source Firestore en mémoire, utilisé pour tester [fr.cklla.pellicule.data.repository.MediaRepositoryImpl]
 * sans SDK Firebase réel. Un seul utilisateur simulé à la fois (pas besoin de plus pour ces tests) :
 * `uid` est ignoré, tout passe par [remoteMedia].
 */
class FakeFirestoreMediaDataSource : FirestoreMediaDataSource {

    val remoteMedia = MutableStateFlow<List<Media>>(emptyList())

    /** Permet de simuler un échec réseau/Firestore dans les tests. */
    var shouldThrowOnWrite = false

    /**
     * Nombre d'erreurs que [observeMedia] lève avant de se comporter normalement : permet de
     * vérifier que le repository réessaye au lieu d'abandonner la synchro définitivement.
     */
    var failedObserveAttempts = 0

    val upsertedMedia = mutableListOf<Media>()
    val deletedMediaIds = mutableListOf<String>()
    var uploadAllCallCount = 0
        private set
    var clearLocalCacheCallCount = 0
        private set
    var observeCallCount = 0
        private set

    override fun observeMedia(uid: String): Flow<List<Media>> = flow {
        observeCallCount++
        if (observeCallCount <= failedObserveAttempts) {
            error("Échec Firestore simulé à l'écoute")
        }
        emitAll(remoteMedia)
    }

    override suspend fun fetchMediaOnce(uid: String): List<Media> = remoteMedia.value

    override suspend fun upsertMedia(uid: String, media: Media) {
        if (shouldThrowOnWrite) error("Échec Firestore simulé")
        upsertedMedia += media
        remoteMedia.update { list -> list.filterNot { it.id == media.id } + media }
    }

    override suspend fun deleteMedia(uid: String, mediaId: String) {
        if (shouldThrowOnWrite) error("Échec Firestore simulé")
        deletedMediaIds += mediaId
        remoteMedia.update { list -> list.filterNot { it.id == mediaId } }
    }

    override suspend fun uploadAll(uid: String, media: List<Media>) {
        if (shouldThrowOnWrite) error("Échec Firestore simulé")
        uploadAllCallCount++
        remoteMedia.update { it + media }
    }

    override suspend fun clearLocalCache() {
        clearLocalCacheCallCount++
    }
}
