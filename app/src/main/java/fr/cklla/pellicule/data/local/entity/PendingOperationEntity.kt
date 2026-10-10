package fr.cklla.pellicule.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Écriture locale pas encore confirmée par le serveur (file d'attente d'envoi, ou « outbox »).
 *
 * [seq] donne l'ordre de rejeu : un média est toujours envoyé avant ses épisodes, et sa suppression
 * après les opérations d'épisodes qui le concernent. Pas de clé étrangère vers `media` : une
 * suppression en attente doit survivre à la ligne qu'elle supprime.
 *
 * Une opération de média ne porte que son identifiant : l'état envoyé est relu dans Room au moment de
 * l'envoi, donc toujours le plus récent. Les opérations d'épisodes portent leurs épisodes dans
 * [episodes], au format `saison:épisode` séparés par des virgules (voir `PendingOperation`).
 */
@Entity(tableName = "pending_operation")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val kind: String,
    val mediaId: String,
    val episodes: String? = null,
)
