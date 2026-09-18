package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implémentation Room du [EpisodeRepository]. */
class EpisodeRepositoryImpl @Inject constructor(
    private val episodeDao: EpisodeDao,
) : EpisodeRepository {

    override fun observeWatchedEpisodes(mediaId: String): Flow<Set<EpisodeKey>> =
        episodeDao.observeWatched(mediaId)
            .map { entities -> entities.map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet() }

    override suspend fun setEpisodeWatched(mediaId: String, episode: EpisodeKey, watched: Boolean): Resource<Unit> =
        runCatching {
            if (watched) {
                episodeDao.markWatched(WatchedEpisodeEntity(mediaId, episode.seasonNumber, episode.episodeNumber))
            } else {
                episodeDao.markUnwatched(mediaId, episode.seasonNumber, episode.episodeNumber)
            }
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Impossible de mettre à jour le statut de l'épisode.", it) },
        )
}
