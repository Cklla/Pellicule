package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.OutboxDao
import fr.cklla.pellicule.data.local.entity.PendingOperationEntity

/** Faux DAO en mémoire de la file d'envoi. */
class FakeOutboxDao : OutboxDao {

    private val operations = mutableListOf<PendingOperationEntity>()
    private var nextSeq = 1L

    val pending: List<PendingOperationEntity> get() = operations.toList()

    override suspend fun insert(operation: PendingOperationEntity): Long {
        val seq = nextSeq++
        operations += operation.copy(seq = seq)
        return seq
    }

    override suspend fun first(): PendingOperationEntity? = operations.minByOrNull { it.seq }

    override suspend fun getAll(): List<PendingOperationEntity> = operations.sortedBy { it.seq }

    override suspend fun delete(seq: Long) {
        operations.removeAll { it.seq == seq }
    }

    override suspend fun deleteForMedia(mediaId: String) {
        operations.removeAll { it.mediaId == mediaId }
    }

    override suspend fun clearAll() {
        operations.clear()
    }
}
