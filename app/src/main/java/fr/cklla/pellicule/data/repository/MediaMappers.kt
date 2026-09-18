package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.entity.MediaEntity
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus

/**
 * Conversions entre le modèle métier [Media] et l'entité Room [MediaEntity].
 *
 * Isolées dans ce fichier plutôt que dans le Repository pour rester testables indépendamment,
 * sans avoir besoin d'une base Room en mémoire.
 */

fun MediaEntity.toDomain(): Media = Media(
    id = id,
    title = title,
    type = MediaType.valueOf(type),
    status = WatchStatus.valueOf(status),
    tmdbId = tmdbId,
    releaseYear = releaseYear,
    posterUrl = posterUrl,
    jellyfinId = jellyfinId,
    rating = rating,
    watchedAt = watchedAt,
)

fun Media.toEntity(): MediaEntity = MediaEntity(
    id = id,
    title = title,
    type = type.name,
    status = status.name,
    tmdbId = tmdbId,
    releaseYear = releaseYear,
    posterUrl = posterUrl,
    jellyfinId = jellyfinId,
    rating = rating,
    watchedAt = watchedAt,
)
