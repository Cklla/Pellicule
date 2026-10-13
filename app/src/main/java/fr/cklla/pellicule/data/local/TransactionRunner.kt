package fr.cklla.pellicule.data.local

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Exécute un bloc dans une transaction Room. Interface séparée de [AppDatabase] pour que les
 * repositories restent testables avec des DAO en mémoire, qui n'ont pas de transaction réelle.
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : TransactionRunner {

    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
