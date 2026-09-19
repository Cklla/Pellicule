package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.JellyfinSessionStore
import fr.cklla.pellicule.data.remote.jellyfin.JellyfinApi
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthRequestDto
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinPushHistoryResult
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
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

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
        // Les appels Jellyfin passent une URL absolue à Retrofit, qui retombe silencieusement sur
        // sa base URL placeholder quand la chaîne reçue est relative : une saisie sans schéma
        // ("192.168.1.10:8096") enverrait donc le mot de passe à un tout autre hôte que celui
        // voulu. On refuse tout ce qui n'est pas explicitement http(s) plutôt que de tenter une
        // correction automatique, qui masquerait une faute de frappe dans l'adresse.
        if (!isSupportedServerUrl(normalizedUrl)) {
            return Resource.Error("L'adresse du serveur doit commencer par http:// ou https://.")
        }
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
            .forEach { media -> runCatching { syncSeries(current, media) }.onFailure(::disconnectIfUnauthorized) }
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
        val watchedFromJellyfin = episodes
            .filter { it.userData?.played == true }
            .mapNotNull { ep ->
                val season = ep.seasonNumber ?: return@mapNotNull null
                val episodeNumber = ep.episodeNumber ?: return@mapNotNull null
                EpisodeKey(season, episodeNumber)
            }
            .toSet()
        // Fusion, jamais remplacement pur : un épisode déjà marqué vu localement ne doit jamais
        // être "dévu" par le pull, y compris quand Jellyfin répond "non vu" pour un item qu'il
        // retrouve bien (serveur réinstallé, historique de lecture reparti de zéro) — Jellyfin fait
        // foi pour ajouter du vu, jamais pour en retirer.
        val watched = episodeRepository.observeWatchedEpisodes(media.id).first() + watchedFromJellyfin
        episodeRepository.replaceWatchedEpisodes(media.id, watched)

        // Liste vide = pas d'info exploitable (série non trouvée côté Jellyfin, ou réponse
        // incomplète) : on laisse le statut local tel quel plutôt que de le remettre à "à voir".
        if (episodes.isEmpty()) return
        val statusFromJellyfin = when {
            watched.size >= episodes.size -> WatchStatus.VU
            watched.isNotEmpty() -> WatchStatus.EN_COURS
            else -> WatchStatus.A_VOIR
        }
        // Même principe que pour les épisodes ci-dessus : le pull ne fait jamais régresser un
        // statut déjà plus avancé, il ne peut que le faire progresser.
        val newStatus = maxOf(resolvedMedia.status, statusFromJellyfin)
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
                val statusFromJellyfin = when {
                    item.userData?.played == true -> WatchStatus.VU
                    (item.userData?.playbackPositionTicks ?: 0L) > 0L -> WatchStatus.EN_COURS
                    else -> WatchStatus.A_VOIR
                }
                // Ne jamais régresser un statut déjà plus avancé (même raison que pour les séries,
                // voir `syncSeries`) : un film redevenu "non vu" côté Jellyfin (historique perdu)
                // ne doit pas repasser "à voir" dans Pellicule.
                val newStatus = maxOf(media.status, statusFromJellyfin)
                if (item.id != media.jellyfinId || newStatus != media.status) {
                    mediaRepository.updateMedia(media.copy(jellyfinId = item.id, status = newStatus))
                }
            }
        }.onFailure(::disconnectIfUnauthorized)
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
        }.onFailure(::disconnectIfUnauthorized)
    }

    override suspend fun pushMovieWatched(media: Media, watched: Boolean) {
        val current = session.value ?: return
        runCatching {
            // Pas `resolveJellyfinId` : son `getItems` filtre par défaut sur `IncludeItemTypes=Series`
            // (pensé pour les séries), un film n'y apparaîtrait donc jamais. Même filtre explicite que
            // `syncTrackedMovies`.
            val jellyfinId = media.jellyfinId ?: run {
                val tmdbId = media.tmdbId ?: return
                jellyfinApi.getItems(
                    url = JellyfinApi.itemsUrl(current.serverUrl, current.userId),
                    authHeader = authHeader(current.accessToken),
                    includeItemTypes = "Movie",
                ).items.firstOrNull { it.providerIds?.get("Tmdb") == tmdbId.toString() }?.id ?: return
            }
            val url = JellyfinApi.playedItemUrl(current.serverUrl, current.userId, jellyfinId)
            if (watched) {
                jellyfinApi.markPlayed(url, authHeader(current.accessToken))
            } else {
                jellyfinApi.markUnplayed(url, authHeader(current.accessToken))
            }
        }.onFailure(::disconnectIfUnauthorized)
    }

    override suspend fun pushWatchedHistory(items: List<Media>): JellyfinPushHistoryResult {
        val current = session.value ?: return JellyfinPushHistoryResult(0, 0)
        var moviesMarkedPlayed = 0
        var episodesMarkedPlayed = 0

        val movies = items.filter { it.type == MediaType.FILM && it.tmdbId != null && it.status == WatchStatus.VU }
        if (movies.isNotEmpty()) {
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
                movies.forEach movieLoop@{ media ->
                    val item = itemByTmdbId[media.tmdbId] ?: return@movieLoop
                    if (item.userData?.played == true) return@movieLoop
                    jellyfinApi.markPlayed(JellyfinApi.playedItemUrl(current.serverUrl, current.userId, item.id), authHeader(current.accessToken))
                    moviesMarkedPlayed++
                }
            }.onFailure(::disconnectIfUnauthorized)
        }

        items.filter { it.type == MediaType.SERIE || it.type == MediaType.ANIME }
            .forEach seriesLoop@{ media ->
                runCatching {
                    val jellyfinId = resolveJellyfinId(current, media) ?: return@seriesLoop
                    val locallyWatched = episodeRepository.observeWatchedEpisodes(media.id).first()
                    if (locallyWatched.isEmpty()) return@seriesLoop
                    val jellyfinEpisodes = jellyfinApi.getSeriesEpisodes(
                        url = JellyfinApi.seriesEpisodesUrl(current.serverUrl, jellyfinId),
                        authHeader = authHeader(current.accessToken),
                        userId = current.userId,
                    ).items
                    jellyfinEpisodes.forEach episodeLoop@{ ep ->
                        val season = ep.seasonNumber ?: return@episodeLoop
                        val episodeNumber = ep.episodeNumber ?: return@episodeLoop
                        if (EpisodeKey(season, episodeNumber) !in locallyWatched) return@episodeLoop
                        if (ep.userData?.played == true) return@episodeLoop
                        jellyfinApi.markPlayed(JellyfinApi.playedItemUrl(current.serverUrl, current.userId, ep.id), authHeader(current.accessToken))
                        episodesMarkedPlayed++
                    }
                }.onFailure(::disconnectIfUnauthorized)
            }

        return JellyfinPushHistoryResult(moviesMarkedPlayed, episodesMarkedPlayed)
    }

    /**
     * Un 401 sur un appel authentifié signifie que le token n'est plus valide côté serveur (révoqué,
     * expiré) : on efface la session locale pour repasser l'app en "déconnecté" plutôt que de
     * continuer à échouer silencieusement à chaque synchro sans jamais le signaler à l'utilisateur.
     */
    private fun disconnectIfUnauthorized(error: Throwable) {
        if (error is HttpException && error.code() == 401) {
            sessionStore.clear()
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

    /** Une adresse exploitable doit porter un schéma http(s) explicite et un hôte non vide. */
    private fun isSupportedServerUrl(url: String): Boolean {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        return (scheme == "http" || scheme == "https") && url.substringAfter("://").isNotBlank()
    }

    /** Ce serveur exige le schéma `MediaBrowser` sur `Authorization` pour tous les appels, y compris authentifiés (`Token=`) — voir doc de [JellyfinApi]. */
    private fun authHeader(token: String? = null) = buildString {
        append("MediaBrowser Client=\"Pellicule\", Device=\"Android\", DeviceId=\"${sessionStore.deviceId}\", Version=\"1.0\"")
        if (token != null) append(", Token=\"$token\"")
    }
}
