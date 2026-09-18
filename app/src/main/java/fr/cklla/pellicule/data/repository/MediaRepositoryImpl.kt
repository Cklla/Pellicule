package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.MediaRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Implémentation Room du [MediaRepository].
 *
 * Room-only pour l'instant : pas de synchro Firestore tant que le projet Firebase n'est pas
 * câblé côté app. L'interface [MediaRepository] est déjà pensée pour que l'ajout de la synchro
 * plus tard n'en change pas la signature publique.
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
) : MediaRepository {

    override fun observeMedia(): Flow<List<Media>> =
        mediaDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeMediaById(id: String): Flow<Media?> =
        mediaDao.observeById(id).map { it?.toDomain() }

    override suspend fun addMedia(media: Media): Resource<String> = runCatching {
        val id = media.id.ifBlank { UUID.randomUUID().toString() }
        mediaDao.insert(media.copy(id = id).toEntity())
        id
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter ce contenu au suivi.", it) },
    )

    override suspend fun updateMedia(media: Media): Resource<Unit> = runCatching {
        mediaDao.update(media.toEntity())
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour ce contenu.", it) },
    )

    override suspend fun deleteMedia(id: String): Resource<Unit> = runCatching {
        mediaDao.deleteById(id)
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer ce contenu du suivi.", it) },
    )
}
