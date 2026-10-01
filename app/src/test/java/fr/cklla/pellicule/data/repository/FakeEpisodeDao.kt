package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Faux DAO en mémoire, utilisé pour tester [EpisodeRepositoryImpl] sans base Room réelle. */
class FakeEpisodeDao : EpisodeDao {

    private val watched = MutableStateFlow<List<WatchedEpisodeEntity>>(emptyList())

    override fun observeWatched(mediaId: String): Flow<List<WatchedEpisodeEntity>> =
        watched.map { list -> list.filter { it.mediaId == mediaId } }

    override suspend fun markWatched(episode: WatchedEpisodeEntity) {
        watched.update { list -> if (list.contains(episode)) list else list + episode }
    }

    override suspend fun markUnwatched(mediaId: String, seasonNumber: Int, episodeNumber: Int) {
        watched.update { list ->
            list.filterNot { it.mediaId == mediaId && it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
        }
    }

    override suspend fun deleteAllForMedia(mediaId: String) {
        watched.update { list -> list.filterNot { it.mediaId == mediaId } }
    }

    override suspend fun insertAll(episodes: List<WatchedEpisodeEntity>) {
        watched.update { list -> list + episodes.filterNot { list.contains(it) } }
    }
}
