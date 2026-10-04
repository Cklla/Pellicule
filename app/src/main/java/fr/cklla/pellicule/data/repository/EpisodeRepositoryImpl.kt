package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import fr.cklla.pellicule.data.remote.firestore.FirestoreMediaDataSource
import fr.cklla.pellicule.di.ApplicationScope
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Implémentation [EpisodeRepository] : Room d'abord (seule source lue par l'UI), puis le même
 * changement répercuté dans le document Firestore du contenu, pour que les épisodes vus survivent à
 * une réinstallation (voir `MediaRepositoryImpl`, qui les restaure depuis Firestore).
 */
class EpisodeRepositoryImpl @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val firestoreDataSource: FirestoreMediaDataSource,
    private val authRepository: AuthRepository,
    @ApplicationScope private val repositoryScope: CoroutineScope,
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
            if (watched) {
                episodeDao.markWatched(WatchedEpisodeEntity(mediaId, episode.seasonNumber, episode.episodeNumber))
            } else {
                episodeDao.markUnwatched(mediaId, episode.seasonNumber, episode.episodeNumber)
            }
            pushToFirestore(mediaId, added = if (watched) setOf(episode) else emptySet(), removed = if (watched) emptySet() else setOf(episode))
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Impossible de mettre à jour le statut de l'épisode.", it) },
        )

    override suspend fun replaceWatchedEpisodes(mediaId: String, watched: Set<EpisodeKey>): Resource<Unit> =
        runCatching {
            val before = episodeDao.getWatchedOnce(mediaId).map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet()
            episodeDao.replaceAllForMedia(
                mediaId,
                watched.map { WatchedEpisodeEntity(mediaId, it.seasonNumber, it.episodeNumber) },
            )
            pushToFirestore(mediaId, added = watched - before, removed = before - watched)
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Impossible de synchroniser les épisodes vus.", it) },
        )

    // Best-effort, comme les écritures de `MediaRepositoryImpl` : une erreur Firestore ne fait jamais
    // échouer l'écriture locale. Chaque envoi part dans son propre coroutine, démarré sans attendre
    // (`UNDISPATCHED`) : l'écriture est ainsi émise, donc mise en file par le SDK, dans l'ordre des
    // appels, alors que l'attente de sa confirmation (qui ne vient jamais hors ligne) ne bloque ni
    // l'appelant ni les cochages suivants. Une file en mémoire attendrait cette confirmation et
    // perdrait les envois en attente si l'app était tuée hors ligne.
    private fun pushToFirestore(mediaId: String, added: Set<EpisodeKey>, removed: Set<EpisodeKey>) {
        val uid = authRepository.currentUser.value?.uid ?: return
        if (added.isNotEmpty()) {
            repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
                runCatching { firestoreDataSource.addWatchedEpisodes(uid, mediaId, added) }
            }
        }
        if (removed.isNotEmpty()) {
            repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
                runCatching { firestoreDataSource.removeWatchedEpisodes(uid, mediaId, removed) }
            }
        }
    }
}
