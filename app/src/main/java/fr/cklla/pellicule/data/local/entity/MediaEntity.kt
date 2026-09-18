package fr.cklla.pellicule.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représentation d'un contenu suivi telle que stockée dans Room.
 *
 * [type] et [status] sont stockés en texte (nom des enums) plutôt qu'en entier : plus verbeux en
 * base, mais lisible en cas d'inspection manuelle et robuste à un changement d'ordre des valeurs.
 *
 * [id] est un UUID (généré côté Repository, pas par Room) plutôt qu'un entier auto-incrémenté :
 * avec la synchro Firestore à venir, cet id doit être stable et unique sur tous les appareils.
 */
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val status: String,
    val tmdbId: Long?,
    val releaseYear: Int?,
    val posterUrl: String?,
    val jellyfinId: String? = null,
    val rating: Int? = null,
    val watchedAt: Long? = null,
)
