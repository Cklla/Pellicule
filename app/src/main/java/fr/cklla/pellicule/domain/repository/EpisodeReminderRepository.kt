package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.EpisodeReminder
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Rappels « me prévenir à la sortie de chaque épisode », stockés uniquement sur l'appareil (Room) :
 * un rappel n'a de sens que là où la notification s'affichera, il ne suit donc pas le compte.
 */
interface EpisodeReminderRepository {

    /** Vrai tant que le rappel du contenu est activé ; faux par défaut. */
    fun observeEnabled(mediaId: String): Flow<Boolean>

    suspend fun setEnabled(mediaId: String, enabled: Boolean): Resource<Unit>

    /** Rappels activés, avec le dernier épisode déjà notifié de chacun. */
    suspend fun getEnabledReminders(): List<EpisodeReminder>

    /** Retient [episode] comme dernier épisode notifié du contenu, pour ne pas le renotifier. */
    suspend fun markNotified(mediaId: String, episode: EpisodeKey)
}
