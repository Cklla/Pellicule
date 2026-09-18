package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.SynopsisRepository

/** Faux repository TMDB (synopsis), pour tester [DetailViewModel] sans appel réseau réel. */
class FakeSynopsisRepository : SynopsisRepository {

    var response: Resource<String?> = Resource.Success(null)

    override suspend fun getSynopsis(tmdbId: Long, type: MediaType): Resource<String?> = response
}
