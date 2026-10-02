package fr.cklla.pellicule.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.cklla.pellicule.domain.usecase.CheckRecapNotificationUseCase

/**
 * Tâche périodique quotidienne (voir `PelliculeApplication`) qui délègue au cas d'usage l'envoi de
 * la notification du récap annuel. Purement locale : la décision ne repose que sur la date de
 * l'appareil, sans Firebase Cloud Messaging.
 */
@HiltWorker
class RecapNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkRecapNotification: CheckRecapNotificationUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        checkRecapNotification()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "recap_notification_daily_check"
    }
}
