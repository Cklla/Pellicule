package fr.cklla.pellicule.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import fr.cklla.pellicule.domain.model.Media
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implémentation [FirestoreMediaDataSource] adossée au SDK `FirebaseFirestore`.
 *
 * L'instance est récupérée via un `Provider` et jamais gardée dans un champ : [clearLocalCache]
 * termine l'instance courante, qui devient définitivement inutilisable, et c'est le
 * `FirebaseFirestore.getInstance()` suivant qui en reconstruit une neuve. Un champ mémorisé une
 * fois pour toutes ferait planter tout ce qui suit une déconnexion.
 */
class FirestoreMediaDataSourceImpl @Inject constructor(
    private val firestoreProvider: Provider<FirebaseFirestore>,
) : FirestoreMediaDataSource {

    private val firestore: FirebaseFirestore
        get() = firestoreProvider.get()

    private fun mediaCollection(uid: String) =
        firestore.collection("users").document(uid).collection("movies")

    // `callbackFlow` + `awaitClose` : le listener Firestore est annulé proprement dès que le
    // collecteur (côté `MediaRepositoryImpl`, via `collectLatest` sur l'utilisateur courant) se
    // désabonne, sans quoi il continuerait à tourner pour un utilisateur qui s'est déconnecté.
    override fun observeMedia(uid: String): Flow<List<Media>> = callbackFlow {
        val registration = mediaCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val media = snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.data?.let { mapToMedia(doc.id, it) }
            }
            trySend(media)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchMediaOnce(uid: String): List<Media> =
        mediaCollection(uid).get().await().documents.mapNotNull { doc ->
            doc.data?.let { mapToMedia(doc.id, it) }
        }

    override suspend fun upsertMedia(uid: String, media: Media) {
        mediaCollection(uid).document(media.id).set(media.toFirestoreMap()).await()
    }

    override suspend fun deleteMedia(uid: String, mediaId: String) {
        mediaCollection(uid).document(mediaId).delete().await()
    }

    // Écriture groupée pour le bootstrap (upload du suivi local pré-existant à la première
    // connexion) : un seul batch plutôt qu'un `upsertMedia` par contenu, atomique côté Firestore.
    override suspend fun uploadAll(uid: String, media: List<Media>) {
        if (media.isEmpty()) return
        val collection = mediaCollection(uid)
        val batch = firestore.batch()
        media.forEach { item -> batch.set(collection.document(item.id), item.toFirestoreMap()) }
        batch.commit().await()
    }

    // `clearPersistence()` exige qu'aucune opération ne soit en cours, d'où le `terminate()`
    // juste avant : il ferme les listeners restants et libère le fichier de cache.
    override suspend fun clearLocalCache() {
        val instance = firestore
        instance.terminate().await()
        instance.clearPersistence().await()
    }
}
