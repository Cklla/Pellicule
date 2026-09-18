package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.Resource

/**
 * Point d'accès à la recherche de contenus via l'API externe (TMDB).
 *
 * Séparé de [MediaRepository] : ce repository ne touche jamais au suivi local, il ne fait
 * qu'interroger TMDB. C'est le ViewModel de l'écran Recherche qui orchestre les deux (recherche
 * TMDB + ajout au suivi via [MediaRepository]).
 */
interface MediaSearchRepository {

    /** Recherche des contenus (films/séries/anime) par titre. Une requête vide renvoie une liste vide sans appel réseau. */
    suspend fun searchMedia(query: String): Resource<List<MediaSearchResult>>
}
