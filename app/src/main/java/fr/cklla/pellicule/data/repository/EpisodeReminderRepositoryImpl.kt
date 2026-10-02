package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeReminderDao
import fr.cklla.pellicule.data.local.entity.EpisodeReminderEntity
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.EpisodeReminder
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.EpisodeReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implémentation Room du [EpisodeReminderRepository]. */
class EpisodeReminderRepositoryImpl @Inject constructor(
    private val dao: EpisodeReminderDao,
) : EpisodeReminderRepository {

    override fun observeEnabled(mediaId: String): Flow<Boolean> =
        dao.observe(mediaId).map { it?.enabled == true }

    // Échoue si le contenu a été retiré du suivi entre-temps (clé étrangère) : rien à rappeler.
    override suspend fun setEnabled(mediaId: String, enabled: Boolean): Resource<Unit> =
        runCatching { dao.setEnabled(mediaId, enabled) }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Impossible de modifier le rappel.", it) },
        )

    override suspend fun getEnabledReminders(): List<EpisodeReminder> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun markNotified(mediaId: String, episode: EpisodeKey) {
        dao.markNotified(mediaId, episode.seasonNumber, episode.episodeNumber)
    }

    private fun EpisodeReminderEntity.toDomain(): EpisodeReminder {
        val season = lastNotifiedSeason
        val episode = lastNotifiedEpisode
        return EpisodeReminder(mediaId, if (season != null && episode != null) EpisodeKey(season, episode) else null)
    }
}
