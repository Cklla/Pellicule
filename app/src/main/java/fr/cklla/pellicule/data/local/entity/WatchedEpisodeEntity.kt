package fr.cklla.pellicule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Marque un épisode d'un contenu suivi comme vu. Pas de colonne "watched" : l'existence de la
 * ligne suffit (un épisode non vu n'a simplement pas de ligne), ce qui rend le retrait aussi
 * simple que l'ajout (suppression de la ligne).
 *
 * `onDelete = CASCADE` : retirer un contenu du suivi supprime aussi ses épisodes marqués vus,
 * pour ne pas laisser de lignes orphelines en base.
 */
@Entity(
    tableName = "watched_episode",
    primaryKeys = ["mediaId", "seasonNumber", "episodeNumber"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId")],
)
data class WatchedEpisodeEntity(
    val mediaId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
)
