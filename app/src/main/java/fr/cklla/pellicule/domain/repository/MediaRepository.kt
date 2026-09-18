package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique aux données suivies pour les ViewModels.
 *
 * Le ViewModel ne connaît que cette interface : il ignore si les contenus viennent de Room, de
 * Firestore ou d'un cache mémoire. L'implémentation (voir `data.repository.MediaRepositoryImpl`)
 * orchestre Room seul pour l'instant ; la synchro Firestore viendra s'y ajouter.
 */
interface MediaRepository {

    /** Flux de l'ensemble des contenus suivis, mis à jour automatiquement à chaque changement. */
    fun observeMedia(): Flow<List<Media>>

    /** Flux d'un contenu précis, ou `null` s'il n'existe pas (ou plus). */
    fun observeMediaById(id: String): Flow<Media?>

    /** Ajoute un nouveau contenu au suivi. Retourne l'id (UUID) généré en cas de succès. */
    suspend fun addMedia(media: Media): Resource<String>

    /** Met à jour un contenu existant (statut, etc.). */
    suspend fun updateMedia(media: Media): Resource<Unit>

    /** Retire un contenu du suivi. */
    suspend fun deleteMedia(id: String): Resource<Unit>
}
