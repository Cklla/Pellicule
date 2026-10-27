package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.MovieRuntimeRepository

/** Faux repository TMDB (durée des films), pour tester [DetailViewModel] sans appel réseau réel. */
class FakeMovieRuntimeRepository : MovieRuntimeRepository {

    var response: Resource<Int?> = Resource.Success(null)
    var callCount = 0
        private set

    override suspend fun getRuntimeMinutes(tmdbId: Long): Resource<Int?> {
        callCount++
        return response
    }
}
