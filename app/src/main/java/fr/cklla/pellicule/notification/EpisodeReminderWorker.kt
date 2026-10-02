package fr.cklla.pellicule.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.cklla.pellicule.domain.usecase.CheckEpisodeRemindersUseCase

/**
 * Tâche périodique quotidienne (voir `PelliculeApplication`) qui délègue au cas d'usage le contrôle
 * des rappels de sortie d'épisodes. Purement locale : aucun serveur de notifications, la décision
 * ne repose que sur la date de l'appareil et sur les dates TMDB en cache.
 *
 * `@HiltWorker`/`@AssistedInject` : seul moyen d'injecter des dépendances dans un `Worker`, que
 * WorkManager instancie lui-même via `HiltWorkerFactory` (branchée dans `PelliculeApplication`).
 */
@HiltWorker
class EpisodeReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkEpisodeReminders: CheckEpisodeRemindersUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        checkEpisodeReminders()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "episode_reminders_daily_check"
    }
}
