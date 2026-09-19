package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinPushHistoryResult
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Faux [JellyfinRepository], utilisé pour tester les ViewModels sans dépendre du vrai connecteur réseau/stockage. */
class FakeJellyfinRepository : JellyfinRepository {

    private val _session = MutableStateFlow<JellyfinSession?>(null)
    override val session: StateFlow<JellyfinSession?> = _session.asStateFlow()

    var connectResult: Resource<Unit> = Resource.Success(Unit)
    val syncedItems = mutableListOf<List<Media>>()
    val syncedMovies = mutableListOf<List<Media>>()
    val pushedEpisodes = mutableListOf<Triple<Media, EpisodeKey, Boolean>>()
    val pushedMovies = mutableListOf<Pair<Media, Boolean>>()
    var pushWatchedHistoryResult = JellyfinPushHistoryResult(moviesMarkedPlayed = 0, episodesMarkedPlayed = 0)
    val pushedHistoryItems = mutableListOf<List<Media>>()

    fun setSession(session: JellyfinSession?) {
        _session.value = session
    }

    override suspend fun connect(serverUrl: String, username: String, password: String): Resource<Unit> {
        if (connectResult is Resource.Success) {
            _session.value = JellyfinSession(serverUrl, userId = "user-1", username = username, accessToken = "fake-token")
        }
        return connectResult
    }

    override fun disconnect() {
        _session.value = null
    }

    override suspend fun syncTrackedSeries(items: List<Media>) {
        syncedItems.add(items)
    }

    override suspend fun syncTrackedMovies(items: List<Media>) {
        syncedMovies.add(items)
    }

    override suspend fun pushEpisodeWatched(media: Media, seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        if (_session.value == null) return
        pushedEpisodes.add(Triple(media, EpisodeKey(seasonNumber, episodeNumber), watched))
    }

    override suspend fun pushMovieWatched(media: Media, watched: Boolean) {
        if (_session.value == null) return
        pushedMovies.add(media to watched)
    }

    override suspend fun pushWatchedHistory(items: List<Media>): JellyfinPushHistoryResult {
        if (_session.value == null) return JellyfinPushHistoryResult(0, 0)
        pushedHistoryItems.add(items)
        return pushWatchedHistoryResult
    }
}
