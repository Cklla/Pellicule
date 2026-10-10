package fr.cklla.pellicule.data.sync

import fr.cklla.pellicule.data.local.OutboxDao
import fr.cklla.pellicule.data.local.entity.PendingOperationEntity
import fr.cklla.pellicule.domain.model.EpisodeKey

/**
 * Nature d'une écriture en attente d'envoi. Stockée par son nom dans `pending_operation.kind`.
 */
enum class PendingOperationKind {
    UPSERT_MEDIA,
    DELETE_MEDIA,
    ADD_EPISODES,
    REMOVE_EPISODES,
}

val PendingOperationEntity.operationKind: PendingOperationKind?
    get() = runCatching { PendingOperationKind.valueOf(kind) }.getOrNull()

/**
 * Épisodes d'une opération d'épisodes, au format `saison:épisode` séparés par des virgules. Une chaîne
 * plutôt qu'une ligne par épisode : une synchro Jellyfin peut en changer plusieurs centaines d'un coup,
 * et ils partent alors en une seule requête.
 */
fun PendingOperationEntity.episodeKeys(): Set<EpisodeKey> =
    episodes.orEmpty().split(',').mapNotNull { raw ->
        val parts = raw.split(':')
        val season = parts.getOrNull(0)?.toIntOrNull()
        val episode = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size == 2 && season != null && episode != null) EpisodeKey(season, episode) else null
    }.toSet()

private fun Set<EpisodeKey>.encode(): String =
    sorted().joinToString(",") { "${it.seasonNumber}:${it.episodeNumber}" }

/**
 * Les trois fonctions suivantes ajoutent une opération à la file ; elles s'appellent dans la même
 * transaction que l'écriture Room qu'elles accompagnent, pour qu'aucune des deux n'existe sans l'autre.
 */
suspend fun OutboxDao.enqueueUpsert(mediaId: String) {
    insert(PendingOperationEntity(kind = PendingOperationKind.UPSERT_MEDIA.name, mediaId = mediaId))
}

/**
 * Les opérations en attente du même contenu sont retirées : le serveur supprime lui-même ses épisodes
 * avec lui, et les rejouer pointerait vers un contenu qui n'existera plus.
 */
suspend fun OutboxDao.enqueueDelete(mediaId: String) {
    deleteForMedia(mediaId)
    insert(PendingOperationEntity(kind = PendingOperationKind.DELETE_MEDIA.name, mediaId = mediaId))
}

suspend fun OutboxDao.enqueueEpisodes(kind: PendingOperationKind, mediaId: String, episodes: Set<EpisodeKey>) {
    require(kind == PendingOperationKind.ADD_EPISODES || kind == PendingOperationKind.REMOVE_EPISODES)
    if (episodes.isEmpty()) return
    insert(PendingOperationEntity(kind = kind.name, mediaId = mediaId, episodes = episodes.encode()))
}
