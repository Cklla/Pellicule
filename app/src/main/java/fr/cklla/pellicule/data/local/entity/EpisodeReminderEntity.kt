package fr.cklla.pellicule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Rappel de sortie d'épisodes d'un contenu suivi, propre à l'appareil (jamais synchronisé).
 *
 * Une ligne par contenu. [enabled] est conservé à `false` plutôt que de supprimer la ligne quand
 * l'utilisateur éteint le rappel : le dernier épisode notifié ([lastNotifiedSeason]/
 * [lastNotifiedEpisode]) survit ainsi à un éteint/rallumé le jour même, qui ne renotifie pas.
 *
 * `onDelete = CASCADE` : retirer un contenu du suivi supprime son rappel.
 */
@Entity(
    tableName = "episode_reminder",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpisodeReminderEntity(
    @PrimaryKey val mediaId: String,
    val enabled: Boolean,
    val lastNotifiedSeason: Int?,
    val lastNotifiedEpisode: Int?,
)
