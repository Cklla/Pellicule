package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Statut vu/non-vu des épisodes d'un contenu suivi (série/anime), stocké localement (Room).
 *
 * Séparé de [MediaRepository] et de [TvDetailsRepository] : ce repository ne connaît ni le
 * contenu suivi lui-même ni les métadonnées TMDB des épisodes (titre, image), seulement quels
 * épisodes sont marqués vus. Le ViewModel de l'écran Détail fait la jointure entre les deux.
 */
interface EpisodeRepository {

    /** Épisodes marqués vus pour un contenu suivi, mis à jour automatiquement à chaque changement. */
    fun observeWatchedEpisodes(mediaId: String): Flow<Set<EpisodeKey>>

    /** Marque un épisode vu ou non vu pour un contenu suivi. */
    suspend fun setEpisodeWatched(mediaId: String, episode: EpisodeKey, watched: Boolean): Resource<Unit>

    /**
     * Remplace l'intégralité du set d'épisodes vus d'un contenu suivi par [watched], en une seule
     * transaction. Utilisé par la synchro Jellyfin (voir `JellyfinRepository.syncTrackedSeries`),
     * qui fait foi côté lecture plutôt que de rejouer des toggles un par un.
     */
    suspend fun replaceWatchedEpisodes(mediaId: String, watched: Set<EpisodeKey>): Resource<Unit>
}
