package fr.cklla.pellicule.domain.usecase

import fr.cklla.pellicule.domain.computeNextEpisode
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.TvShowInfoRepository
import fr.cklla.pellicule.domain.statusAfterEpisodeChange
import fr.cklla.pellicule.domain.util.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Chemin d'écriture unique pour cocher ou décocher un épisode : la liste d'épisodes de la fiche
 * Détail et le "+1" de la Bibliothèque passent tous deux par ici.
 *
 * Dans l'ordre : écriture de l'épisode (`watched_episode`), recalcul du statut global du contenu
 * (et donc de `watchedAt`, que le repository dérive de la transition de statut), puis poussée vers
 * Jellyfin. Les deux premières étapes sont purement locales et ne dépendent jamais du réseau : le
 * calcul s'appuie sur le cache des métadonnées TMDB tel quel, sans le rafraîchir. Elles s'exécutent
 * hors annulation pour qu'un écran quitté en cours de route ne laisse pas un épisode coché avec un
 * statut resté en arrière. La poussée Jellyfin est best-effort (voir
 * [JellyfinRepository.pushEpisodeWatched]).
 */
class SetEpisodeWatchedUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val episodeRepository: EpisodeRepository,
    private val tvShowInfoRepository: TvShowInfoRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val timeSource: TimeSource,
) {

    /**
     * @param onLocalChange appelé avec le contenu tel qu'il est en base une fois la partie locale
     *   terminée, avant la poussée Jellyfin : permet à l'appelant de rafraîchir sa copie de travail
     *   (statut, `watchedAt`) sans attendre le réseau.
     * @return le contenu à jour, ou `null` si le contenu n'existe plus ou n'a pas d'épisodes (film).
     */
    suspend operator fun invoke(
        mediaId: String,
        episode: EpisodeKey,
        watched: Boolean,
        onLocalChange: (Media) -> Unit = {},
    ): Media? {
        val media = withContext(NonCancellable) { applyLocally(mediaId, episode, watched) } ?: return null
        onLocalChange(media)
        // Le contenu poussé porte déjà le nouveau statut : la résolution de `jellyfinId` côté
        // Jellyfin réécrit le contenu tel qu'on le lui passe et ne doit pas ramener l'ancien statut.
        jellyfinRepository.pushEpisodeWatched(media, episode.seasonNumber, episode.episodeNumber, watched)
        return media
    }

    private suspend fun applyLocally(mediaId: String, episode: EpisodeKey, watched: Boolean): Media? {
        val media = mediaRepository.observeMediaById(mediaId).first() ?: return null
        if (media.type == MediaType.FILM) return null
        if (episodeRepository.setEpisodeWatched(mediaId, episode, watched) is Resource.Error) return null

        val watchedEpisodes = episodeRepository.observeWatchedEpisodes(mediaId).first()
        val show = media.tmdbId?.let { tvShowInfoRepository.getCachedShow(it) }
        val next = computeNextEpisode(show, watchedEpisodes, AirDate.fromMillis(timeSource.nowMillis()))
        val newStatus = statusAfterEpisodeChange(media.status, watched, next)
        if (newStatus == media.status) return media

        mediaRepository.updateMedia(media.copy(status = newStatus))
        // Relecture : `watchedAt` est posé par le repository, pas par la copie qu'on vient d'écrire.
        return mediaRepository.observeMediaById(mediaId).first() ?: media.copy(status = newStatus)
    }
}
