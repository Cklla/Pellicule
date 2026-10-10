package fr.cklla.pellicule.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Demande l'envoi des écritures en attente, dès que le réseau le permet. */
fun interface OutboxScheduler {
    fun requestFlush()
}

/**
 * Envoi par WorkManager : la file survit à un processus tué, et l'envoi attend le réseau tout seul.
 * `REPLACE` : une nouvelle demande remplace l'envoi en cours ou en attente de réessai, ce qui relance
 * aussi un réessai différé dès qu'une nouvelle écriture arrive. Interrompre un envoi en cours ne perd
 * rien, une opération ne quitte la file qu'une fois confirmée par le serveur.
 */
class WorkManagerOutboxScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : OutboxScheduler {

    override fun requestFlush() {
        val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(OutboxFlushWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}

@HiltWorker
class OutboxFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: MediaSyncer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncer.flush()) {
        FlushResult.DONE -> Result.success()
        FlushResult.RETRY_LATER -> Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "outbox_flush"
    }
}
