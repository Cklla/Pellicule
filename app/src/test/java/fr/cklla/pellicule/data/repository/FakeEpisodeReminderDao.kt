package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeReminderDao
import fr.cklla.pellicule.data.local.entity.EpisodeReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Faux DAO en mémoire des rappels, pour tester sans base Room réelle (pas de clé étrangère simulée). */
class FakeEpisodeReminderDao : EpisodeReminderDao {

    private val reminders = MutableStateFlow<Map<String, EpisodeReminderEntity>>(emptyMap())

    override fun observe(mediaId: String): Flow<EpisodeReminderEntity?> = reminders.map { it[mediaId] }

    override suspend fun get(mediaId: String): EpisodeReminderEntity? = reminders.value[mediaId]

    override suspend fun getEnabled(): List<EpisodeReminderEntity> = reminders.value.values.filter { it.enabled }

    override suspend fun upsert(reminder: EpisodeReminderEntity) {
        reminders.update { it + (reminder.mediaId to reminder) }
    }

    override suspend fun markNotified(mediaId: String, seasonNumber: Int, episodeNumber: Int) {
        reminders.update { current ->
            val existing = current[mediaId] ?: return@update current
            current + (mediaId to existing.copy(lastNotifiedSeason = seasonNumber, lastNotifiedEpisode = episodeNumber))
        }
    }
}
