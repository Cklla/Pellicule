package fr.cklla.pellicule.notification

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import fr.cklla.pellicule.R

/**
 * Canaux de notification de l'app. Un nouveau type de notification s'ajoute en déclarant une entrée
 * ici : [createNotificationChannels] les crée toutes au démarrage.
 */
enum class AppNotificationChannel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val importance: Int,
) {
    NEW_EPISODES(
        id = "nouveaux_episodes",
        nameRes = R.string.notification_channel_new_episodes_name,
        descriptionRes = R.string.notification_channel_new_episodes_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),
    ANNUAL_RECAP(
        id = "recap_annuel",
        nameRes = R.string.notification_channel_recap_name,
        descriptionRes = R.string.notification_channel_recap_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),
}

/**
 * Crée les canaux de [AppNotificationChannel], idempotent (recréer un canal existant ne change
 * rien). Appelé au démarrage de l'app plutôt qu'au premier envoi : les canaux doivent exister pour
 * que l'utilisateur puisse les configurer dans les paramètres système avant toute notification.
 * Sans effet avant Android 8, qui ne connaît pas les canaux.
 */
fun createNotificationChannels(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    AppNotificationChannel.entries.forEach { channel ->
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(channel.id, channel.importance)
                .setName(context.getString(channel.nameRes))
                .setDescription(context.getString(channel.descriptionRes))
                .build(),
        )
    }
}
