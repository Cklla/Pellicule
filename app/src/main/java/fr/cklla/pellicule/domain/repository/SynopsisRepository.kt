package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource

/** Synopsis TMDB d'un contenu, récupéré à la demande (jamais mis en cache localement). */
interface SynopsisRepository {
    suspend fun getSynopsis(tmdbId: Long, type: MediaType): Resource<String?>
}
