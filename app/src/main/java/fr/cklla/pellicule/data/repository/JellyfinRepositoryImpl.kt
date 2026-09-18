package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.JellyfinSessionStore
import fr.cklla.pellicule.data.remote.jellyfin.JellyfinApi
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthRequestDto
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Implémentation [JellyfinRepository] : auth par login utilisateur, résolution des items par
 * `ProviderIds.Tmdb` (bibliothèque indexée TMDB, voir CLAUDE.md), et réconciliation Room via
 * [EpisodeRepository] / [MediaRepository]. Une série sans `tmdbId` local (ajoutée hors recherche)
 * n'est jamais mappable, elle est ignorée par la synchro.
 */
class JellyfinRepositoryImpl @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val sessionStore: JellyfinSessionStore,
    private val mediaRepository: MediaRepository,
    private val episodeRepository: EpisodeRepository,
) : JellyfinRepository {

    override val session: StateFlow<JellyfinSession?> = sessionStore.session

    override suspend fun connect(serverUrl: String, username: String, password: String): Resource<Unit> {
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        return runCatching {
            val response = jellyfinApi.authenticateByName(
                url = JellyfinApi.authenticateByNameUrl(normalizedUrl),
                authHeader = authHeader(),
                body = JellyfinAuthRequestDto(username = username, password = password),
            )
            sessionStore.save(
                JellyfinSession(
                    serverUrl = normalizedUrl,
                    userId = response.user.id,
                    username = response.user.name,
                    accessToken = response.accessToken,
                ),
            )
        }.fold(
            onSuccess = { Resource.Success(Unit) },
            onFailure = { Resource.Error("Connexion au serveur Jellyfin impossible. Vérifie l'URL et les identifiants.", it) },
        )
    }

    override fun disconnect() = sessionStore.clear()

    override suspend fun syncTrackedSeries(items: List<Media>) {
        val current = session.value ?: return
        items.filter { it.type == MediaType.SERIE || it.type == MediaType.ANIME }
            .forEach { media -> runCatching { syncSeries(current, media) } }
    }

    private suspend fun syncSeries(current: JellyfinSession, media: Media) {
        val jellyfinId = resolveJellyfinId(current, media) ?: return
        val episodes = jellyfinApi.getSeriesEpisodes(
            url = JellyfinApi.seriesEpisodesUrl(current.serverUrl, jellyfinId),
            token = current.accessToken,
            userId = current.userId,
        ).items
        val watched = episodes
            .filter { it.userData?.played == true }
            .mapNotNull { ep ->
                val season = ep.seasonNumber ?: return@mapNotNull null
                val episodeNumber = ep.episodeNumber ?: return@mapNotNull null
                EpisodeKey(season, episodeNumber)
            }
            .toSet()
        episodeRepository.replaceWatchedEpisodes(media.id, watched)
    }

    override suspend fun pushEpisodeWatched(media: Media, seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        val current = session.value ?: return
        runCatching {
            val jellyfinId = resolveJellyfinId(current, media) ?: return
            val episodeItemId = jellyfinApi.getSeriesEpisodes(
                url = JellyfinApi.seriesEpisodesUrl(current.serverUrl, jellyfinId),
                token = current.accessToken,
                userId = current.userId,
            ).items.firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }?.id ?: return

            val url = JellyfinApi.playedItemUrl(current.serverUrl, current.userId, episodeItemId)
            if (watched) {
                jellyfinApi.markPlayed(url, current.accessToken)
            } else {
                jellyfinApi.markUnplayed(url, current.accessToken)
            }
        }
    }

    /** Résout `Media.jellyfinId` à partir du `tmdbId`, et le persiste pour éviter de re-résoudre à chaque synchro. */
    private suspend fun resolveJellyfinId(current: JellyfinSession, media: Media): String? {
        media.jellyfinId?.let { return it }
        val tmdbId = media.tmdbId ?: return null

        val items = jellyfinApi.getItems(
            url = JellyfinApi.itemsUrl(current.serverUrl, current.userId),
            token = current.accessToken,
        ).items
        val resolvedId = items.firstOrNull { it.providerIds?.get("Tmdb") == tmdbId.toString() }?.id ?: return null

        mediaRepository.updateMedia(media.copy(jellyfinId = resolvedId))
        return resolvedId
    }

    private fun authHeader() =
        "MediaBrowser Client=\"Pellicule\", Device=\"Android\", DeviceId=\"${sessionStore.deviceId}\", Version=\"1.0\""
}
