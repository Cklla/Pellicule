package fr.cklla.pellicule.data.remote.firestore

import fr.cklla.pellicule.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la collection Firestore d'un utilisateur, sur le modèle métier [Media] plutôt que sur des
 * types Firestore bruts. Interface séparée de son implémentation (contrairement à `FirestoreMappers`,
 * simples fonctions) pour pouvoir la remplacer par un fake en test unitaire — `FirebaseFirestore`
 * est une classe du SDK Android, impossible à instancier dans un test JVM sans Robolectric/mock.
 *
 * Chemin Firestore : `users/{uid}/movies/{mediaId}` — chaque utilisateur ne voit que sa propre
 * collection (voir `firestore.rules`).
 */
interface FirestoreMediaDataSource {

    /** Écoute temps réel de la collection de l'utilisateur [uid]. */
    fun observeMedia(uid: String): Flow<List<Media>>

    /** Lecture ponctuelle (pas d'écoute), utilisée pour le bootstrap au premier lancement. */
    suspend fun fetchMediaOnce(uid: String): List<Media>

    suspend fun upsertMedia(uid: String, media: Media)

    suspend fun deleteMedia(uid: String, mediaId: String)

    /** Écriture groupée, utilisée pour l'upload initial du suivi local pré-existant. */
    suspend fun uploadAll(uid: String, media: List<Media>)

    /**
     * Efface la copie que Firestore garde sur le disque de l'appareil. Appelé à la déconnexion :
     * vider la base Room ne suffit pas, le SDK conserve de son côté son propre cache des documents
     * du compte qui vient de se déconnecter.
     */
    suspend fun clearLocalCache()
}
