package fr.cklla.pellicule.domain.usecase

import fr.cklla.pellicule.domain.calendar.episodeToNotify
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.notification.EpisodeNotifier
import fr.cklla.pellicule.domain.repository.EpisodeReminderRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.TvShowInfoRepository
import fr.cklla.pellicule.domain.util.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Contrôle quotidien des rappels : pour chaque contenu qui a un rappel actif, rafraîchit sa fiche
 * TMDB (au plus une fois par jour) puis notifie l'épisode qui sort aujourd'hui, une seule fois.
 * Seuls les contenus avec un rappel actif sont interrogés sur TMDB.
 */
class CheckEpisodeRemindersUseCase @Inject constructor(
    private val reminderRepository: EpisodeReminderRepository,
    private val mediaRepository: MediaRepository,
    private val tvShowInfoRepository: TvShowInfoRepository,
    private val notifier: EpisodeNotifier,
    private val timeSource: TimeSource,
) {

    /** Retourne le nombre de notifications affichées. */
    suspend operator fun invoke(): Int {
        val reminders = reminderRepository.getEnabledReminders()
        if (reminders.isEmpty()) return 0

        val today = AirDate.fromMillis(timeSource.nowMillis())
        val tracked = mediaRepository.observeMedia().first().associateBy { it.id }
        var notified = 0
        for (reminder in reminders) {
            // Contenu retiré du suivi entre-temps : rien à annoncer, et pas d'appel TMDB pour lui.
            val media = tracked[reminder.mediaId] ?: continue
            val tmdbId = media.tmdbId ?: continue
            try {
                tvShowInfoRepository.refreshOncePerDay(tmdbId)
                val info = tvShowInfoRepository.getCachedShow(tmdbId) ?: continue
                val episode = episodeToNotify(today, info, reminder.lastNotified) ?: continue
                if (notifier.notify(media.id, media.title, episode.key)) {
                    reminderRepository.markNotified(media.id, episode.key)
                    notified++
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Un contenu en échec ne doit pas empêcher les autres d'être notifiés.
            }
        }
        return notified
    }
}
