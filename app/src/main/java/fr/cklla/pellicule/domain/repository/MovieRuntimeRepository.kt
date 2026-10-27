package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.Resource

/** Durée TMDB d'un film, récupérée à la demande (jamais mise en cache localement). */
interface MovieRuntimeRepository {
    /** Durée en minutes, `null` quand TMDB ne la connaît pas. */
    suspend fun getRuntimeMinutes(tmdbId: Long): Resource<Int?>
}
