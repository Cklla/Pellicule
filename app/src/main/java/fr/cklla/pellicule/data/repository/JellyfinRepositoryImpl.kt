package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.JellyfinSessionStore
import fr.cklla.pellicule.data.remote.jellyfin.JellyfinApi
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthRequestDto
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Implémentation [JellyfinRepository] : auth par login utilisateur, résolution des items par
 * `ProviderIds.Tmdb` (bibliothèque indexée TMDB côté serveur), et réconciliation Room via
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
        // `media` peut avoir un `jellyfinId` pas encore à jour (résolu à l'instant par
        // `resolveJellyfinId`) : on repart de cette version pour ne pas écraser la valeur qu'on
        // vient de persister lors de la mise à jour du statut plus bas.
        val resolvedMedia = media.copy(jellyfinId = jellyfinId)
        val episodes = jellyfinApi.getSeriesEpisodes(
            url = JellyfinApi.seriesEpisodesUrl(current.serverUrl, jellyfinId),
            authHeader = authHeader(current.accessToken),
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

        // Liste vide = pas d'info exploitable (série non trouvée côté Jellyfin, ou réponse
        // incomplète) : on laisse le statut local tel quel plutôt que de le remettre à "à voir".
        if (episodes.isEmpty()) return
        val newStatus = when {
            watched.size >= episodes.size -> WatchStatus.VU
            watched.isNotEmpty() -> WatchStatus.EN_COURS
            else -> WatchStatus.A_VOIR
        }
        if (newStatus != resolvedMedia.status) {
            mediaRepository.updateMedia(resolvedMedia.copy(status = newStatus))
        }
    }

    override suspend fun syncTrackedMovies(items: List<Media>) {
        val current = session.value ?: return
        val movies = items.filter { it.type == MediaType.FILM && it.tmdbId != null }
        if (movies.isEmpty()) return
        runCatching {
            val jellyfinItems = jellyfinApi.getItems(
                url = JellyfinApi.itemsUrl(current.serverUrl, current.userId),
                authHeader = authHeader(current.accessToken),
                includeItemTypes = "Movie",
                fields = "ProviderIds,UserData",
            ).items
            val itemByTmdbId = jellyfinItems
                .mapNotNull { item -> item.providerIds?.get("Tmdb")?.toLongOrNull()?.let { it to item } }
                .toMap()

            movies.forEach { media ->
                val item = itemByTmdbId[media.tmdbId] ?: return@forEach
                val newStatus = when {
                    item.userData?.played == true -> WatchStatus.VU
                    (item.userData?.playbackPositionTicks ?: 0L) > 0L -> WatchStatus.EN_COURS
                    else -> WatchStatus.A_VOIR
                }
                if (item.id != media.jellyfinId || newStatus != media.status) {
                    mediaRepository.updateMedia(media.copy(jellyfinId = item.id, status = newStatus))
                }
            }
        }
    }

    override suspend fun pushEpisodeWatched(media: Media, seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        val current = session.value ?: return
        runCatching {
            val jellyfinId = resolveJellyfinId(current, media) ?: return
            val episodeItemId = jellyfinApi.getSeriesEpisodes(
                url = JellyfinApi.seriesEpisodesUrl(current.serverUrl, jellyfinId),
                authHeader = authHeader(current.accessToken),
                userId = current.userId,
            ).items.firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }?.id ?: return

            val url = JellyfinApi.playedItemUrl(current.serverUrl, current.userId, episodeItemId)
            if (watched) {
                jellyfinApi.markPlayed(url, authHeader(current.accessToken))
            } else {
                jellyfinApi.markUnplayed(url, authHeader(current.accessToken))
            }
        }
    }

    /** Résout `Media.jellyfinId` à partir du `tmdbId`, et le persiste pour éviter de re-résoudre à chaque synchro. */
    private suspend fun resolveJellyfinId(current: JellyfinSession, media: Media): String? {
        media.jellyfinId?.let { return it }
        val tmdbId = media.tmdbId ?: return null

        val items = jellyfinApi.getItems(
            url = JellyfinApi.itemsUrl(current.serverUrl, current.userId),
            authHeader = authHeader(current.accessToken),
        ).items
        val resolvedId = items.firstOrNull { it.providerIds?.get("Tmdb") == tmdbId.toString() }?.id ?: return null

        mediaRepository.updateMedia(media.copy(jellyfinId = resolvedId))
        return resolvedId
    }

    /** Ce serveur exige le schéma `MediaBrowser` sur `Authorization` pour tous les appels, y compris authentifiés (`Token=`) — voir doc de [JellyfinApi]. */
    private fun authHeader(token: String? = null) = buildString {
        append("MediaBrowser Client=\"Pellicule\", Device=\"Android\", DeviceId=\"${sessionStore.deviceId}\", Version=\"1.0\"")
        if (token != null) append(", Token=\"$token\"")
    }
}
