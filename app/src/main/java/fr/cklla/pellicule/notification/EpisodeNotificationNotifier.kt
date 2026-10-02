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
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.notification.EpisodeNotifier
import fr.cklla.pellicule.ui.permission.canPostNotifications
import java.util.Objects
import javax.inject.Inject

/**
 * Notification locale « <titre> : l'épisode N sort aujourd'hui ». Un tap ouvre la fiche Détail du
 * contenu, via la destination générique portée par l'`Intent` (voir [NotificationDestination]).
 */
class EpisodeNotificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : EpisodeNotifier {

    // La permission est vérifiée juste avant par `canPostNotifications`.
    @SuppressLint("MissingPermission")
    override fun notify(mediaId: String, title: String, episode: EpisodeKey): Boolean {
        if (!canPostNotifications(context)) return false

        // Un identifiant par contenu + saison + épisode : sert à la fois d'identifiant de
        // notification (un même épisode ne s'affiche jamais deux fois) et de code de requête du
        // PendingIntent, sans quoi deux notifications partageraient la même destination.
        val notificationId = Objects.hash(mediaId, episode.seasonNumber, episode.episodeNumber)
        val openIntent = NotificationDestination(NotificationDestinationType.DETAIL, mediaId).putInto(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(R.string.notification_new_episode_text, title, episode.episodeNumber)

        val notification = NotificationCompat.Builder(context, AppNotificationChannel.NEW_EPISODES.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_new_episode_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }
}
