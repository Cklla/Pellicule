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
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun unauthorizedException() =
    HttpException(Response.error<Any>(401, "".toResponseBody("text/plain".toMediaType())))

class JellyfinRepositoryImplTest {

    private val session = JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token-abc")

    private val logged = mutableListOf<Triple<Int, String, Throwable?>>()
    private val defaultSink = SyncLog.sink

    init {
        SyncLog.sink = { priority, message, error -> logged += Triple(priority, message, error) }
    }

    @After
    fun restoreSyncLog() {
        SyncLog.sink = defaultSink
    }

    private fun repository(
        api: FakeJellyfinApi = FakeJellyfinApi(),
        sessionStore: FakeJellyfinSessionStore = FakeJellyfinSessionStore(),
        mediaRepository: MediaRepository = fakeMediaRepository(FakeMediaDao()),
        episodeRepository: EpisodeRepositoryImpl = fakeEpisodeRepository(),
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
    fun `une adresse sans schema est refusee sans appel reseau`() = runTest {
        // Le serveur répondrait avec succès : seule la validation d'URL peut faire échouer ce cas.
        val api = FakeJellyfinApi().apply {
            authResponse = JellyfinAuthResponseDto(accessToken = "token-abc", user = JellyfinUserDto(id = "user-1", name = "stef"))
        }
        val sessionStore = FakeJellyfinSessionStore()
        val repository = repository(api, sessionStore)

        val result = repository.connect("192.168.1.10:8096", "stef", "secret")

        assertTrue(result is Resource.Error)
        assertNull(sessionStore.session.first())
    }

    @Test
    fun `une adresse avec un schema non http est refusee`() = runTest {
        val api = FakeJellyfinApi().apply {
            authResponse = JellyfinAuthResponseDto(accessToken = "token-abc", user = JellyfinUserDto(id = "user-1", name = "stef"))
        }
        val sessionStore = FakeJellyfinSessionStore()
        val repository = repository(api, sessionStore)

        val result = repository.connect("file:///data/local/tmp", "stef", "secret")

        assertTrue(result is Resource.Error)
        assertNull(sessionStore.session.first())
    }

    @Test
    fun `une adresse http en clair reste acceptee`() = runTest {
        val api = FakeJellyfinApi().apply {
            authResponse = JellyfinAuthResponseDto(accessToken = "token-abc", user = JellyfinUserDto(id = "user-1", name = "stef"))
        }
        val sessionStore = FakeJellyfinSessionStore()
        val repository = repository(api, sessionStore)

        val result = repository.connect("http://192.168.1.10:8096", "stef", "secret")

        assertTrue(result is Resource.Success)
        assertEquals("http://192.168.1.10:8096", sessionStore.session.first()?.serverUrl)
    }

    @Test
    fun `syncTrackedSeries resout l'id Jellyfin et reconcilie les episodes vus`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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
        val episodeRepository = fakeEpisodeRepository()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(setOf(EpisodeKey(1, 1)), episodeRepository.observeWatchedEpisodes(mediaId).first())
        val updatedMedia = mediaRepository.observeMediaById(mediaId).first()
        assertEquals("jf-series-1", updatedMedia?.jellyfinId)
    }

    @Test
    fun `syncTrackedSeries passe le statut a EN_COURS quand certains episodes sont vus`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.A_VOIR, tmdbId = 95396))
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertTrue(api.items.isEmpty())
    }

    @Test
    fun `syncTrackedMovies ne fait rien sans session active`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1))
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertTrue(api.items.isEmpty())
    }

    @Test
    fun `syncTrackedSeries ne fait rien sans session active`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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
    fun `syncTrackedSeries ne reimpose pas VU a une serie remise En cours quand Jellyfin ne signale plus rien de vu`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Kaamelott", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 100, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        // L'historique local garde une trace des épisodes déjà vus lors d'un premier visionnage :
        // il ne doit plus servir à recalculer le statut, sinon "Vu" se réimposerait indéfiniment.
        val episodeRepository = fakeEpisodeRepository()
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, 1), watched = true)
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, 2), watched = true)
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = false)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.EN_COURS, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `syncTrackedSeries ne passe pas VU quand le suivi local compte plus d'episodes que le serveur`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Kaamelott", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 100, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        val episodeRepository = fakeEpisodeRepository()
        repeat(5) { index -> episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, index + 1), watched = true) }
        // Le serveur n'expose que deux épisodes pour cette série (découpage différent de celui des
        // métadonnées), dont un seul vu : la série est donc en cours, pas vue.
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = true)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.EN_COURS, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `pushSeriesUnwatched demarque la serie en un appel et ne repasse pas par les episodes`() = runTest {
        val media = Media(id = "media-1", title = "Kaamelott", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 100, jellyfinId = "jf-series-1")
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = false)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session))

        repository.pushSeriesUnwatched(media)

        assertEquals(1, api.unplayedUrls.size)
        assertTrue(api.unplayedUrls.first().contains("jf-series-1"))
    }

    @Test
    fun `pushSeriesUnwatched rattrape les episodes encore vus si le serveur n'a pas propage`() = runTest {
        val media = Media(id = "media-1", title = "Kaamelott", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 100, jellyfinId = "jf-series-1")
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = true)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session))

        repository.pushSeriesUnwatched(media)

        assertTrue(api.unplayedUrls.any { it.contains("jf-series-1") })
        assertTrue(api.unplayedUrls.any { it.contains("jf-ep-1") })
        assertTrue(api.unplayedUrls.none { it.contains("jf-ep-2") })
    }

    @Test
    fun `pushSeriesUnwatched ne fait rien sans session active`() = runTest {
        val media = Media(id = "media-1", title = "Kaamelott", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 100, jellyfinId = "jf-series-1")
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(initial = null))

        repository.pushSeriesUnwatched(media)

        assertTrue(api.unplayedUrls.isEmpty())
    }

    @Test
    fun `pushMovieWatched demarque le film sur Jellyfin en utilisant le jellyfinId deja connu`() = runTest {
        val media = Media(id = "media-1", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631, jellyfinId = "jf-movie-1")
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(session))

        repository.pushMovieWatched(media, watched = false)

        assertEquals(1, api.unplayedUrls.size)
        assertTrue(api.unplayedUrls.first().contains("jf-movie-1"))
        assertTrue(api.playedUrls.isEmpty())
    }

    @Test
    fun `pushMovieWatched resout le jellyfinId par tmdbId quand il n'est pas encore connu`() = runTest {
        val media = Media(id = "media-1", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631)
        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631")))
        }
        val repository = repository(api, FakeJellyfinSessionStore(session))

        repository.pushMovieWatched(media, watched = false)

        assertTrue(api.unplayedUrls.first().contains("jf-movie-1"))
    }

    @Test
    fun `pushMovieWatched ne fait rien sans session active`() = runTest {
        val media = Media(id = "media-1", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631, jellyfinId = "jf-movie-1")
        val api = FakeJellyfinApi()
        val repository = repository(api, FakeJellyfinSessionStore(initial = null))

        repository.pushMovieWatched(media, watched = false)

        assertTrue(api.unplayedUrls.isEmpty())
    }

    @Test
    fun `syncTrackedSeries efface la session locale sur un 401`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396))
        val api = FakeJellyfinApi().apply { error = unauthorizedException() }
        val sessionStore = FakeJellyfinSessionStore(session)
        val repository = repository(api, sessionStore, mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertNull(sessionStore.session.first())
    }

    @Test
    fun `syncTrackedMovies efface la session locale sur un 401`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631))
        val api = FakeJellyfinApi().apply { error = unauthorizedException() }
        val sessionStore = FakeJellyfinSessionStore(session)
        val repository = repository(api, sessionStore, mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertNull(sessionStore.session.first())
    }

    @Test
    fun `syncTrackedSeries garde la session sur une erreur reseau autre qu'un 401`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
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

    @Test
    fun `syncTrackedSeries ne fait pas regresser un statut VU et garde les episodes deja vus quand Jellyfin a perdu son historique`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, tmdbId = 94605, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        val episodeRepository = fakeEpisodeRepository()
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, 1), watched = true)
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, 2), watched = true)

        // Serveur réinstallé : l'item est retrouvé (même id), mais son historique de lecture est
        // reparti de zéro (tous les épisodes reviennent "non vus").
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = false)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(setOf(EpisodeKey(1, 1), EpisodeKey(1, 2)), episodeRepository.observeWatchedEpisodes(mediaId).first())
        assertEquals(WatchStatus.VU, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `syncTrackedSeries ne fait regresser aucun statut apres reinstallation quand Jellyfin n'expose plus que la saison suivante non vue`() = runTest {
        listOf(WatchStatus.EN_COURS, WatchStatus.VU).forEach { initialStatus ->
            // Réinstallation : le contenu revient de Firestore avec son statut, mais sans
            // `jellyfinId` ni épisode vu (`watched_episode` n'existe qu'en Room).
            val mediaRepository = fakeMediaRepository(FakeMediaDao())
            val mediaId = (mediaRepository.addMedia(
                Media(title = "Fallout", type = MediaType.SERIE, status = initialStatus, tmdbId = 106379),
            ) as Resource.Success).data
            val episodeRepository = fakeEpisodeRepository()

            // La saison 1 a disparu du serveur : il ne reste que la saison 2, jamais commencée.
            val api = FakeJellyfinApi().apply {
                items = listOf(JellyfinItemDto(id = "jf-fallout", providerIds = mapOf("Tmdb" to "106379")))
                episodesBySeriesId = mapOf(
                    "jf-fallout" to listOf(
                        JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 2, episodeNumber = 1, userData = JellyfinUserDataDto(played = false)),
                        JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 2, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                    ),
                )
            }
            val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

            repository.syncTrackedSeries(mediaRepository.observeMedia().first())

            val updated = mediaRepository.observeMediaById(mediaId).first()
            assertEquals(initialStatus, updated?.status)
            assertEquals("jf-fallout", updated?.jellyfinId)
            assertTrue(episodeRepository.observeWatchedEpisodes(mediaId).first().isEmpty())
        }
    }

    private fun episodes(season: Int, count: Int, playedUpTo: Int, locationType: String? = null) =
        (1..count).map { number ->
            JellyfinEpisodeDto(
                id = "jf-$season-$number",
                seasonNumber = season,
                episodeNumber = number,
                userData = JellyfinUserDataDto(played = number <= playedUpTo),
                locationType = locationType,
            )
        }

    /** Série Vu dont la saison 1 est cochée localement, face à un serveur décrit par [serverEpisodes]. */
    private suspend fun pullOnWatchedSeries(
        serverEpisodes: List<JellyfinEpisodeDto>,
        localWatched: Set<EpisodeKey> = (1..9).map { EpisodeKey(1, it) }.toSet(),
    ): WatchStatus? {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, tmdbId = 94605, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        val episodeRepository = fakeEpisodeRepository()
        localWatched.forEach { episodeRepository.setEpisodeWatched(mediaId, it, watched = true) }
        val api = FakeJellyfinApi().apply { episodesBySeriesId = mapOf("jf-series-1" to serverEpisodes) }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        return mediaRepository.observeMediaById(mediaId).first()?.status
    }

    @Test
    fun `syncTrackedSeries repasse En cours une serie Vu quand une nouvelle saison arrive sur le serveur`() = runTest {
        val status = pullOnWatchedSeries(episodes(1, 9, playedUpTo = 9) + episodes(2, 9, playedUpTo = 0))

        assertEquals(WatchStatus.EN_COURS, status)
    }

    @Test
    fun `syncTrackedSeries coche le premier episode de la nouvelle saison et reste En cours`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, tmdbId = 94605, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        val episodeRepository = fakeEpisodeRepository()
        (1..9).forEach { episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, it), watched = true) }
        val api = FakeJellyfinApi().apply {
            episodesBySeriesId = mapOf("jf-series-1" to episodes(1, 9, playedUpTo = 9) + episodes(2, 9, playedUpTo = 1))
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())
        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertTrue(EpisodeKey(2, 1) in episodeRepository.observeWatchedEpisodes(mediaId).first())
        assertEquals(WatchStatus.EN_COURS, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `syncTrackedSeries repasse Vu la serie quand la nouvelle saison est vue en entier`() = runTest {
        val status = pullOnWatchedSeries(episodes(1, 9, playedUpTo = 9) + episodes(2, 9, playedUpTo = 9))

        assertEquals(WatchStatus.VU, status)
    }

    @Test
    fun `syncTrackedSeries laisse Vu une serie marquee a la main sans aucun episode vu`() = runTest {
        val status = pullOnWatchedSeries(
            serverEpisodes = episodes(1, 9, playedUpTo = 0),
            localWatched = emptySet(),
        )

        assertEquals(WatchStatus.VU, status)
    }

    @Test
    fun `syncTrackedSeries ignore les episodes annonces sans fichier sur le serveur`() = runTest {
        val status = pullOnWatchedSeries(
            episodes(1, 9, playedUpTo = 9) + episodes(2, 9, playedUpTo = 0, locationType = "Virtual"),
        )

        assertEquals(WatchStatus.VU, status)
    }

    @Test
    fun `syncTrackedSeries ignore la saison 0 des specials`() = runTest {
        val specials = listOf(JellyfinEpisodeDto(id = "jf-0-1", seasonNumber = 0, episodeNumber = 1, userData = JellyfinUserDataDto(played = false)))

        val status = pullOnWatchedSeries(episodes(1, 9, playedUpTo = 9) + specials)

        assertEquals(WatchStatus.VU, status)
    }

    /**
     * Arcane après l'ajout de la saison 2 : le serveur a recréé la série, l'ancien id (`jf-old`) répond 404 et
     * le nouvel item (`jf-new`) porte les deux saisons, la saison 2 étant entamée.
     */
    private fun apiWithRecreatedSeries() = FakeJellyfinApi().apply {
        missingIds = setOf("jf-old")
        items = listOf(JellyfinItemDto(id = "jf-new", providerIds = mapOf("Tmdb" to "94605")))
        episodesBySeriesId = mapOf("jf-new" to episodes(1, 9, playedUpTo = 9) + episodes(2, 9, playedUpTo = 1))
    }

    private suspend fun addArcane(mediaRepository: MediaRepository, jellyfinId: String? = "jf-old", status: WatchStatus = WatchStatus.VU) =
        (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = status, tmdbId = 94605, jellyfinId = jellyfinId),
        ) as Resource.Success).data

    @Test
    fun `syncTrackedSeries retrouve une serie recreee sur le serveur et coche la nouvelle saison`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository)
        val episodeRepository = fakeEpisodeRepository()
        (1..9).forEach { episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, it), watched = true) }
        val repository = repository(apiWithRecreatedSeries(), FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        val media = mediaRepository.observeMediaById(mediaId).first()
        assertEquals("jf-new", media?.jellyfinId)
        assertEquals(WatchStatus.EN_COURS, media?.status)
        assertTrue(EpisodeKey(2, 1) in episodeRepository.observeWatchedEpisodes(mediaId).first())
    }

    @Test
    fun `syncTrackedSeries ne change rien quand l'ancien id est introuvable et la serie absente du serveur`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository)
        val api = apiWithRecreatedSeries().apply { items = emptyList() }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        val media = mediaRepository.observeMediaById(mediaId).first()
        assertEquals("jf-old", media?.jellyfinId)
        assertEquals(WatchStatus.VU, media?.status)
    }

    @Test
    fun `un 404 sur un id fraichement resolu n'est pas rejoue`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository, jellyfinId = null)
        val api = apiWithRecreatedSeries().apply { missingIds = setOf("jf-new") }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.VU, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    @Test
    fun `pushEpisodeWatched retrouve une serie recreee sur le serveur`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository)
        val api = apiWithRecreatedSeries()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)
        val media = mediaRepository.observeMediaById(mediaId).first()!!

        repository.pushEpisodeWatched(media, seasonNumber = 2, episodeNumber = 2, watched = true)

        assertEquals(listOf("https://jellyfin.exemple.fr/Users/user-1/PlayedItems/jf-2-2"), api.playedUrls)
        assertEquals("jf-new", mediaRepository.observeMediaById(mediaId).first()?.jellyfinId)
    }

    @Test
    fun `pushSeriesUnwatched retrouve une serie recreee sur le serveur`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository)
        val api = apiWithRecreatedSeries()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.pushSeriesUnwatched(mediaRepository.observeMediaById(mediaId).first()!!)

        assertTrue("https://jellyfin.exemple.fr/Users/user-1/PlayedItems/jf-new" in api.unplayedUrls)
    }

    @Test
    fun `pushWatchedHistory retrouve une serie recreee sur le serveur`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository)
        val episodeRepository = fakeEpisodeRepository()
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(2, 2), watched = true)
        val api = apiWithRecreatedSeries()
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        val result = repository.pushWatchedHistory(mediaRepository.observeMedia().first())

        assertEquals(1, result.episodesMarkedPlayed)
        assertEquals(listOf("https://jellyfin.exemple.fr/Users/user-1/PlayedItems/jf-2-2"), api.playedUrls)
    }

    @Test
    fun `un echec de synchro d'une serie est journalise avec son titre`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        addArcane(mediaRepository)
        val failure = RuntimeException("serveur injoignable")
        val api = apiWithRecreatedSeries().apply { error = failure }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        val (priority, message, error) = logged.single()
        assertEquals(android.util.Log.WARN, priority)
        assertTrue("Arcane" in message)
        assertEquals(failure, error)
    }

    @Test
    fun `un echec de synchro des films ou d'un envoi est journalise`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = addArcane(mediaRepository)
        val movie = Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631)
        val api = apiWithRecreatedSeries().apply { error = RuntimeException("réseau coupé") }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedMovies(listOf(movie))
        repository.pushEpisodeWatched(mediaRepository.observeMediaById(mediaId).first()!!, 2, 2, watched = true)

        assertEquals(2, logged.size)
        assertTrue(logged.all { it.first == android.util.Log.WARN })
    }

    @Test
    fun `un 401 est journalise en plus d'effacer la session`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        addArcane(mediaRepository)
        val sessionStore = FakeJellyfinSessionStore(session)
        val api = apiWithRecreatedSeries().apply { error = unauthorizedException() }
        val repository = repository(api, sessionStore, mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertEquals(1, logged.size)
        assertNull(sessionStore.session.first())
    }

    @Test
    fun `le remplacement d'un id Jellyfin perime est journalise sans echec`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        addArcane(mediaRepository)
        val repository = repository(apiWithRecreatedSeries(), FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        val (priority, message, error) = logged.single()
        assertEquals(android.util.Log.INFO, priority)
        assertTrue("Arcane" in message)
        assertNull(error)
    }

    @Test
    fun `une synchro reussie ne journalise rien`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        addArcane(mediaRepository, jellyfinId = "jf-new")
        val repository = repository(apiWithRecreatedSeries(), FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        assertTrue(logged.isEmpty())
    }

    @Test
    fun `syncTrackedMovies ne fait pas regresser un statut VU quand Jellyfin rapporte le film non vu`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631, jellyfinId = "jf-movie-1"),
        ) as Resource.Success).data
        val api = FakeJellyfinApi().apply {
            items = listOf(
                JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = false)),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        assertEquals(WatchStatus.VU, mediaRepository.observeMediaById(mediaId).first()?.status)
    }

    // 2026-10-04 à midi UTC.
    private val fixedTimeSource = object : TimeSource {
        override fun nowMillis() = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        override val zone = ZoneOffset.UTC
    }

    private val july2024 = ZonedDateTime.of(2024, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `un pull sur une serie Vu dont l'annee a ete choisie ne modifie pas watchedAt`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao(), timeSource = fixedTimeSource)
        // Sans jellyfinId : le pull résout l'id puis réécrit le contenu, c'est le chemin qui passe
        // par `updateMedia` et donc par la dérivation de `watchedAt`.
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, tmdbId = 94605),
        ) as Resource.Success).data
        mediaRepository.setWatchedYear(mediaId, 2024)
        assertEquals(july2024, mediaRepository.observeMediaById(mediaId).first()?.watchedAt)
        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-series-1", providerIds = mapOf("Tmdb" to "94605")))
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = true)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = true)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedSeries(mediaRepository.observeMedia().first())

        val media = mediaRepository.observeMediaById(mediaId).first()
        assertEquals("jf-series-1", media?.jellyfinId)
        assertEquals(WatchStatus.VU, media?.status)
        assertEquals(july2024, media?.watchedAt)
    }

    @Test
    fun `un pull sur un film Vu dont l'annee a ete choisie ne modifie pas watchedAt`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao(), timeSource = fixedTimeSource)
        val mediaId = (mediaRepository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631),
        ) as Resource.Success).data
        mediaRepository.setWatchedYear(mediaId, 2024)
        val api = FakeJellyfinApi().apply {
            items = listOf(
                JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = true)),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        repository.syncTrackedMovies(mediaRepository.observeMedia().first())

        val media = mediaRepository.observeMediaById(mediaId).first()
        assertEquals("jf-movie-1", media?.jellyfinId)
        assertEquals(july2024, media?.watchedAt)
    }

    @Test
    fun `pushWatchedHistory marque vus sur Jellyfin les films et episodes deja vus localement`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val movieId = (mediaRepository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631),
        ) as Resource.Success).data
        val seriesId = (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 94605, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        val episodeRepository = fakeEpisodeRepository()
        episodeRepository.setEpisodeWatched(seriesId, EpisodeKey(1, 1), watched = true)

        val api = FakeJellyfinApi().apply {
            items = listOf(
                JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = false)),
            )
            episodesBySeriesId = mapOf(
                "jf-series-1" to listOf(
                    JellyfinEpisodeDto(id = "jf-ep-1", seasonNumber = 1, episodeNumber = 1, userData = JellyfinUserDataDto(played = false)),
                    JellyfinEpisodeDto(id = "jf-ep-2", seasonNumber = 1, episodeNumber = 2, userData = JellyfinUserDataDto(played = false)),
                ),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository, episodeRepository)

        val result = repository.pushWatchedHistory(mediaRepository.observeMedia().first())

        assertEquals(1, result.moviesMarkedPlayed)
        assertEquals(1, result.episodesMarkedPlayed)
        assertTrue(api.playedUrls.any { it.contains("jf-movie-1") })
        assertTrue(api.playedUrls.any { it.contains("jf-ep-1") })
        assertTrue(api.playedUrls.none { it.contains("jf-ep-2") })
        assertNotNull(movieId)
    }

    @Test
    fun `pushWatchedHistory ne marque rien de deja vu cote Jellyfin`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631))
        val api = FakeJellyfinApi().apply {
            items = listOf(
                JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = true)),
            )
        }
        val repository = repository(api, FakeJellyfinSessionStore(session), mediaRepository)

        val result = repository.pushWatchedHistory(mediaRepository.observeMedia().first())

        assertEquals(0, result.moviesMarkedPlayed)
        assertTrue(api.playedUrls.isEmpty())
    }

    @Test
    fun `pushWatchedHistory ne fait rien sans session active`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        mediaRepository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631))
        val api = FakeJellyfinApi().apply {
            items = listOf(JellyfinItemDto(id = "jf-movie-1", providerIds = mapOf("Tmdb" to "438631"), userData = JellyfinUserDataDto(played = false)))
        }
        val repository = repository(api, FakeJellyfinSessionStore(initial = null), mediaRepository)

        val result = repository.pushWatchedHistory(mediaRepository.observeMedia().first())

        assertEquals(0, result.moviesMarkedPlayed)
        assertEquals(0, result.episodesMarkedPlayed)
        assertTrue(api.playedUrls.isEmpty())
    }
}
