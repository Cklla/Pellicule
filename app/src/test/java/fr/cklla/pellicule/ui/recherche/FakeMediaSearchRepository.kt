package fr.cklla.pellicule.ui.recherche

import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.MediaSearchRepository

/** Faux repository de recherche, pour tester [RechercheViewModel] sans appel TMDB réel. */
class FakeMediaSearchRepository : MediaSearchRepository {

    var response: Resource<List<MediaSearchResult>> = Resource.Success(emptyList())
    var searchCallCount = 0
        private set

    override suspend fun searchMedia(query: String): Resource<List<MediaSearchResult>> {
        searchCallCount++
        return response
    }
}
