package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.OutboxDao
import fr.cklla.pellicule.data.local.TransactionRunner
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import fr.cklla.pellicule.data.sync.OutboxScheduler
import fr.cklla.pellicule.data.sync.PendingOperationKind
import fr.cklla.pellicule.data.sync.enqueueEpisodes
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implémentation [EpisodeRepository] : Room d'abord (seule source lue par l'UI), puis le même
 * changement envoyé au serveur par la file d'envoi, pour que les épisodes vus survivent à une
 * réinstallation (voir `MediaSyncer`, qui les restaure depuis le serveur).
 *
 * Chaque changement est écrit dans Room et dans la file d'envoi dans une même transaction : ni l'un
 * ni l'autre n'existe sans son pendant, et rien ne se perd si l'app est tuée hors ligne.
 */
class EpisodeRepositoryImpl @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val outboxDao: OutboxDao,
    private val transactions: TransactionRunner,
    private val outboxScheduler: OutboxScheduler,
) : EpisodeRepository {

    override fun observeWatchedEpisodes(mediaId: String): Flow<Set<EpisodeKey>> =
        episodeDao.observeWatched(mediaId)
            .map { entities -> entities.map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet() }

    override fun observeAllWatchedEpisodes(): Flow<Map<String, Set<EpisodeKey>>> =
        episodeDao.observeAllWatched().map { entities ->
            entities.groupBy({ it.mediaId }, { EpisodeKey(it.seasonNumber, it.episodeNumber) })
                .mapValues { it.value.toSet() }
        }

    override suspend fun setEpisodeWatched(mediaId: String, episode: EpisodeKey, watched: Boolean): Resource<Unit> =
        runCatching {
            transactions.run {
                if (watched) {
                    episodeDao.markWatched(WatchedEpisodeEntity(mediaId, episode.seasonNumber, episode.episodeNumber))
                } else {
                    episodeDao.markUnwatched(mediaId, episode.seasonNumber, episode.episodeNumber)
                }
                val kind = if (watched) PendingOperationKind.ADD_EPISODES else PendingOperationKind.REMOVE_EPISODES
                outboxDao.enqueueEpisodes(kind, mediaId, setOf(episode))
            }
            outboxScheduler.requestFlush()
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Impossible de mettre à jour le statut de l'épisode.", it) },
        )

    // Seule la différence est mise en file : une synchro Jellyfin qui ne change rien n'envoie rien.
    override suspend fun replaceWatchedEpisodes(mediaId: String, watched: Set<EpisodeKey>): Resource<Unit> =
        runCatching {
            transactions.run {
                val before = episodeDao.getWatchedOnce(mediaId).map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet()
                episodeDao.replaceAllForMedia(
                    mediaId,
                    watched.map { WatchedEpisodeEntity(mediaId, it.seasonNumber, it.episodeNumber) },
                )
                outboxDao.enqueueEpisodes(PendingOperationKind.ADD_EPISODES, mediaId, watched - before)
                outboxDao.enqueueEpisodes(PendingOperationKind.REMOVE_EPISODES, mediaId, before - watched)
            }
            outboxScheduler.requestFlush()
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Impossible de synchroniser les épisodes vus.", it) },
        )
}
