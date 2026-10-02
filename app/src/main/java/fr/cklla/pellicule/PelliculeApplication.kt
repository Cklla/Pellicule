package fr.cklla.pellicule

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import fr.cklla.pellicule.notification.EpisodeReminderWorker
import fr.cklla.pellicule.notification.REMINDER_CHECK_HOUR
import fr.cklla.pellicule.notification.RecapNotificationWorker
import fr.cklla.pellicule.notification.createNotificationChannels
import fr.cklla.pellicule.notification.delayUntilNextHour
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Point d'entrée de l'injection de dépendances Hilt : cette classe déclenche
 * la génération du graphe de dépendances au démarrage de l'application.
 *
 * Implémente `Configuration.Provider` pour que WorkManager (initialisé à la demande, voir le
 * manifeste) utilise `HiltWorkerFactory` : c'est ce qui permet à `EpisodeReminderWorker` de se faire
 * injecter ses dépendances comme n'importe quel autre composant Hilt.
 */
@HiltAndroidApp
class PelliculeApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // App Check doit être installé avant le premier appel à Firestore ou à Auth, sans quoi
        // les requêtes partiraient sans jeton d'attestation. `installAppCheck` a une implémentation
        // par type de build (voir src/debug et src/release) : Play Integrity en release, jeton de
        // debug sinon.
        installAppCheck(this)

        createNotificationChannels(this)
        // Contrôle des rappels de sortie d'épisodes une fois par jour, calé sur le prochain 9 h
        // local à la première planification (WorkManager peut décaler l'exécution selon
        // l'économie d'énergie). `KEEP` : relancer l'app ne doit ni réinitialiser le cycle ni
        // décaler le job déjà planifié. Sans rappel actif, le job ne fait rien.
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            EpisodeReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EpisodeReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextHour(System.currentTimeMillis(), REMINDER_CHECK_HOUR), TimeUnit.MILLISECONDS)
                .build(),
        )
        // Même cadence pour la notification du récap annuel : le job ne fait rien hors de la
        // fenêtre du récap, et une fois l'année notifiée.
        workManager.enqueueUniquePeriodicWork(
            RecapNotificationWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecapNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextHour(System.currentTimeMillis(), REMINDER_CHECK_HOUR), TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}
