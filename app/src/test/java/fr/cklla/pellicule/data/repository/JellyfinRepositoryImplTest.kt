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
        mediaRepository: MediaRepository = fakeMediaRepository(FakeMediaDao()),
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
        val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
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
        val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
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
        val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
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
        val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
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

    @Test
    fun `pushWatchedHistory marque vus sur Jellyfin les films et episodes deja vus localement`() = runTest {
        val mediaRepository = fakeMediaRepository(FakeMediaDao())
        val movieId = (mediaRepository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631),
        ) as Resource.Success).data
        val seriesId = (mediaRepository.addMedia(
            Media(title = "Arcane", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 94605, jellyfinId = "jf-series-1"),
        ) as Resource.Success).data
        val episodeRepository = EpisodeRepositoryImpl(FakeEpisodeDao())
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
