package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchAvailability

/**
 * Plateformes françaises proposant un contenu, récupérées à la demande et jamais mises en cache :
 * les offres changent trop souvent pour qu'une valeur stockée reste fiable.
 */
interface WatchProvidersRepository {
    suspend fun getAvailability(tmdbId: Long, type: MediaType): Resource<WatchAvailability>
}
