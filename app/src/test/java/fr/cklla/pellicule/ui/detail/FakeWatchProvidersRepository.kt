package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.repository.WatchProvidersRepository

/** Faux repository TMDB (plateformes de streaming), pour tester [DetailViewModel] sans appel réseau réel. */
class FakeWatchProvidersRepository : WatchProvidersRepository {

    var response: Resource<WatchAvailability> =
        Resource.Success(WatchAvailability.Known(emptyList(), emptyList(), null))

    override suspend fun getAvailability(tmdbId: Long, type: MediaType): Resource<WatchAvailability> = response
}
