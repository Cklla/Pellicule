package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Faux DAO en mémoire, utilisé pour tester [MediaRepositoryImpl] et les ViewModels sans base Room
 * réelle (pas besoin d'instrumentation Android pour ces tests).
 */
class FakeMediaDao : MediaDao {

    private val media = MutableStateFlow<List<MediaEntity>>(emptyList())

    override fun observeAll(): Flow<List<MediaEntity>> = media

    override fun observeById(id: String): Flow<MediaEntity?> =
        media.map { list -> list.find { it.id == id } }

    override suspend fun insert(media: MediaEntity) {
        this.media.update { list -> list.filterNot { it.id == media.id } + media }
    }

    override suspend fun update(media: MediaEntity) {
        this.media.update { list -> list.map { if (it.id == media.id) media else it } }
    }

    override suspend fun deleteById(id: String) {
        media.update { list -> list.filterNot { it.id == id } }
    }
}
