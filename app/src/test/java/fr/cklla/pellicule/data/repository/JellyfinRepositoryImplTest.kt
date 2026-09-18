package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthResponseDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinEpisodeDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinItemDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinUserDataDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinUserDto
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun unauthorizedException() =
    HttpException(Response.error<Any>(401, "".toResponseBody("text/plain".toMediaType())))

class JellyfinRepositoryImplTest {

    private val session = JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token-abc")

    private fun repository(
        api: FakeJellyfinApi = FakeJellyfinApi(),
        sessionStore: FakeJellyfinSessionStore = FakeJellyfinSessionStore(),
        mediaRepository: MediaRepositoryImpl = MediaRepositoryImpl(FakeMediaDao()),
        episodeRepository: EpisodeRepositoryImpl = EpisodeRepositoryImpl(FakeEpisodeDao()),
    ) = JellyfinRepositoryImpl(api, sessionStore, mediaRepository, episodeRepository)

    @Test
    fun `une connexion reussie persiste la session`() = runTest {
        val api = FakeJellyfinApi().apply {
            authResponse = JellyfinAuthResponseDto(accessToken = "token-abc", user = JellyfinUserDto(id = "user-1", name = "stef"))
        }
        val sessionStore = FakeJellyfinSessionStore()
        val repository = repository(api, sessionStore)

        val result = repository.connect("https://jellyfin.exemple.fr", "stef", "secret")

        assertTrue(result is Resource.Success)
        assertEquals("stef", sessionStore.session.first()?.username)
        assertEquals("user-1", sessionStore.session.first()?.userId)
    }

    @Test
    fun `une connexion echouee ne persiste rien`() = runTest {
        val api = FakeJellyfinApi().apply { authError = RuntimeException("401") }
        val sessionStore = FakeJellyfinSessionStore()
        val repository = repository(api, sessionStore)

        val result = repository.connect("https://jellyfin.exemple.fr", "stef", "mauvais-mdp")

        assertTrue(result is Resource.Error)
        assertNull(sessionStore.session.first())
    }

    @Test
    fun `syncTrackedSeries resout l'id Jellyfin et reconcilie les episodes vus`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        ) as Resource.Success).data

        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-series-1", providerIds = mapOf("Tmdb" to "95396")))
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = true)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(setOf(EpisodeKey(1, 1)), episodeRepository.observeWatchedEpisodes(mediaId).first())
        val updatedMedia = mediaRepository.observeMediaById(mediaId).first()
        assertEquals("jf-series-1", updatedMedia?.jellyfinId)
    }

    @Test
    fun `syncTrackedSeries passe le statut a EN_COURS quand certains episodes sont vus`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.A_VOIR, tmdbId = 95396),
        ) as Resource.Success).data

        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-series-1", providerIds = mapOf("Tmdb" to "95396")))
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = true)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        val updatedMedia = mediaRepository.observeMediaById(mediaId).first()
        assertEquals(WatchStatus.EN_COURS, updatedMedia?.status)
        assertEquals("jf-series-1", updatedMedia?.jellyfinId)
    }

    @Test
    fun `syncTrackedSeries passe le statut a VU quand tous les episodes sont vus`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        ) as Resource.Success).data

        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-series-1", providerIds = mapOf("Tmdb" to "95396")))
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = true)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = true)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.VU, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `syncTrackedMovies passe le statut a EN_COURS puis VU selon Jellyfin`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631),
        ) as Resource.Success).data

        val api = FakeJellyfinApi().apply {
            items = listOf(
                JellyfinItemDto(
                    id = "jf-movie-1",
                    providerIds = mapOf("Tmdb" to "438631"),
                    userData = JellyfinUserDataDto(played = false, playbackPositionTicks = 12_000_000),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        val afterStart = mediaRepository.observeMediaById(mediaId).first()
        assertEquals(WatchStatus.EN_COURS, afterStart?.status)
        assertEquals("jf-movie-1", afterStart?.jellyfinId)

        api.items = listOf(
            JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = true)),
        )
        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.VU, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `syncTrackedMovies ignore les series`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.A_VOIR, tmdbId = 95396))
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertTrue(api.items.isEmpty())
    }

    @Test
    fun `syncTrackedMovies ne fait rien sans session active`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631),
        ) as Resource.Success).data
        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = true)))
        }
        val repository = repository(api, FakeJellyfinSessionStore(initial = null), mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.A_VOIR, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `syncTrackedSeries ignore les films`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1))
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertTrue(api.items.isEmpty())
    }

    @Test
    fun `syncTrackedSeries ne fait rien sans session active`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396))
        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-series-1", providerIds = mapOf("Tmdb" to "95396")))
        }
        val repository = repository(api, FakeJellyfinSessionStore(initial = null), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertTrue(repository.session.first() == null)
    }

    @Test
    fun `pushEpisodeWatched marque l'episode vu sur Jellyfin`() = runTest {
        val media = Media(id = "media-1", title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396, jellyfinId = "jf-series-1")
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1)),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session))

        repository.pushEpisodeWatched(media, seasonNumber = 1, episodeNumber = 1, watched = true)

        assertEquals(1, api.playedUrls.size)
        assertTrue(api.playedUrls.first().contains("jf-ep-1"))
        assertTrue(api.unplayedUrls.isEmpty())
    }

    @Test
    fun `syncTrackedSeries efface la session locale sur un 401`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396))
        val api = FakeJellyfinApi().apply { error = unauthorizedException() }
        val sessionStore = FakeJellyfinSessionStore(session)
        val repository = repository(api, sessionStore, mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertNull(sessionStore.session.first())
    }

    @Test
    fun `syncTrackedMovies efface la session locale sur un 401`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631))
        val api = FakeJellyfinApi().apply { error = unauthorizedException() }
        val sessionStore = FakeJellyfinSessionStore(session)
        val repository = repository(api, sessionStore, mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertNull(sessionStore.session.first())
    }

    @Test
    fun `syncTrackedSeries garde la session sur une erreur reseau autre qu'un 401`() = runTest {
        val mediaRepository = MediaRepositoryImpl(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396))
        val api = FakeJellyfinApi().apply { error = RuntimeException("timeout") }
        val sessionStore = FakeJellyfinSessionStore(session)
        val repository = repository(api, sessionStore, mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertNotNull(sessionStore.session.first())
    }

    @Test
    fun `pushEpisodeWatched efface la session locale sur un 401`() = runTest {
        val media = Media(id = "media-1", title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, jellyfinId = "jf-series-1")
        val api = FakeJellyfinApi().apply { error = unauthorizedException() }
        val sessionStore = FakeJellyfinSessionStore(session)
        val repository = repository(api, sessionStore)

        repository.pushEpisodeWatched(media, seasonNumber = 1, episodeNumber = 1, watched = true)

        assertNull(sessionStore.session.first())
    }

    @Test
    fun `pushEpisodeWatched ne fait rien sans session active`() = runTest {
        val media = Media(id = "media-1", title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, jellyfinId = "jf-series-1")
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(initial = null))

        repository.pushEpisodeWatched(media, seasonNumber = 1, episodeNumber = 1, watched = true)

        assertTrue(api.playedUrls.isEmpty())
        assertNotNull(media)
    }
}
