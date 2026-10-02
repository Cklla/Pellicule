package fr.cklla.pellicule.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.pellicule.MainActivity
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.notification.RecapNotifier
import fr.cklla.pellicule.ui.permission.canPostNotifications
import javax.inject.Inject

/** Notification locale « Ton récap <année> est prêt » ; un tap ouvre l'écran du récap de cette année. */
class RecapNotificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecapNotifier {

    // La permission est vérifiée juste avant par `canPostNotifications`.
    @SuppressLint("MissingPermission")
    override fun notify(year: Int): Boolean {
        if (!canPostNotifications(context)) return false

        val openIntent = NotificationDestination(NotificationDestinationType.RECAP, year.toString()).putInto(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        // L'année sert de code de requête : une année = un PendingIntent distinct.
        val pendingIntent = PendingIntent.getActivity(
            context,
            year,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, AppNotificationChannel.ANNUAL_RECAP.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_recap_title, year))
            .setContentText(context.getString(R.string.notification_recap_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(year, notification)
        return true
    }
}
