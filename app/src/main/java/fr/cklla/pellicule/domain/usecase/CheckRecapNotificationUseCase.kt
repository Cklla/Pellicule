package fr.cklla.pellicule.domain.usecase

import fr.cklla.pellicule.domain.notification.RecapNotificationStore
import fr.cklla.pellicule.domain.notification.RecapNotifier
import fr.cklla.pellicule.domain.recap.recapYearToNotify
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.domain.util.today
import javax.inject.Inject

/**
 * Contrôle quotidien du récap annuel : notifie à la première exécution dans la fenêtre du récap,
 * une seule fois par année. L'année n'est marquée comme notifiée que si la notification a vraiment
 * été affichée : sans permission, elle reste due et partira si la permission est accordée avant la
 * fin de la fenêtre.
 */
class CheckRecapNotificationUseCase @Inject constructor(
    private val store: RecapNotificationStore,
    private val notifier: RecapNotifier,
    private val timeSource: TimeSource,
) {
    operator fun invoke() {
        val year = recapYearToNotify(timeSource.today(), store::hasNotified) ?: return
        if (notifier.notify(year)) store.markNotified(year)
    }
}
