package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.pellicule.data.repository.EpisodeReminderRepositoryImpl
import fr.cklla.pellicule.data.repository.fakeEpisodeRepository
import fr.cklla.pellicule.data.repository.FakeEpisodeReminderDao
import fr.cklla.pellicule.data.repository.FakeJellyfinRepository
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.FakeTvShowInfoRepository
import fr.cklla.pellicule.data.repository.fakeMediaRepository
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.model.WatchProvider
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.EpisodeReminderRepository
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.usecase.SetEpisodeWatchedUseCase
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // 2026-10-08 à midi UTC.
    private var now = 1_791_460_800_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelFor(
        mediaId: String,
        mediaRepository: MediaRepository,
        tvDetailsRepository: FakeTvDetailsRepository = FakeTvDetailsRepository(),
        episodeRepository: EpisodeRepository = fakeEpisodeRepository(),
        jellyfinRepository: JellyfinRepository = FakeJellyfinRepository(),
        synopsisRepository: FakeSynopsisRepository = FakeSynopsisRepository(),
        watchProvidersRepository: FakeWatchProvidersRepository = FakeWatchProvidersRepository(),
        tvShowInfoRepository: FakeTvShowInfoRepository = FakeTvShowInfoRepository(),
        reminderRepository: EpisodeReminderRepository = EpisodeReminderRepositoryImpl(FakeEpisodeReminderDao()),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PelliculeDestinations.DETAIL_ARG_MEDIA_ID to mediaId)),
        mediaRepository = mediaRepository,
        tvDetailsRepository = tvDetailsRepository,
        episodeRepository = episodeRepository,
        jellyfinRepository = jellyfinRepository,
        synopsisRepository = synopsisRepository,
        watchProvidersRepository = watchProvidersRepository,
        setEpisodeWatched = SetEpisodeWatchedUseCase(
            mediaRepository,
            episodeRepository,
            tvShowInfoRepository,
            jellyfinRepository,
            TimeSource { 1_790_000_000_000L },
        ),
        tvShowInfoRepository = tvShowInfoRepository,
        reminderRepository = reminderRepository,
        timeSource = TimeSource { now },
    )

    private fun previewViewModelFor(
        mediaRepository: MediaRepository,
        tmdbId: Long = 42,
        title: String = "Severance",
        type: String = "SERIE",
        year: String = "2022",
        posterUrl: String = "",
        tvDetailsRepository: FakeTvDetailsRepository = FakeTvDetailsRepository(),
        episodeRepository: EpisodeRepository = fakeEpisodeRepository(),
        jellyfinRepository: JellyfinRepository = FakeJellyfinRepository(),
        synopsisRepository: FakeSynopsisRepository = FakeSynopsisRepository(),
        watchProvidersRepository: FakeWatchProvidersRepository = FakeWatchProvidersRepository(),
        tvShowInfoRepository: FakeTvShowInfoRepository = FakeTvShowInfoRepository(),
        reminderRepository: EpisodeReminderRepository = EpisodeReminderRepositoryImpl(FakeEpisodeReminderDao()),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                PelliculeDestinations.DETAIL_APERCU_ARG_TMDB_ID to tmdbId,
                PelliculeDestinations.DETAIL_APERCU_ARG_TITLE to title,
                PelliculeDestinations.DETAIL_APERCU_ARG_TYPE to type,
                PelliculeDestinations.DETAIL_APERCU_ARG_YEAR to year,
                PelliculeDestinations.DETAIL_APERCU_ARG_POSTER_URL to posterUrl,
            ),
        ),
        mediaRepository = mediaRepository,
        tvDetailsRepository = tvDetailsRepository,
        episodeRepository = episodeRepository,
        jellyfinRepository = jellyfinRepository,
        synopsisRepository = synopsisRepository,
        watchProvidersRepository = watchProvidersRepository,
        setEpisodeWatched = SetEpisodeWatchedUseCase(
            mediaRepository,
            episodeRepository,
            tvShowInfoRepository,
            jellyfinRepository,
            TimeSource { 1_790_000_000_000L },
        ),
        tvShowInfoRepository = tvShowInfoRepository,
        reminderRepository = reminderRepository,
        timeSource = TimeSource { now },
    )

    @Test
    fun `changer le statut persiste la mise a jour`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(mediaId, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.VU)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WatchStatus.VU, viewModel.uiState.value.media?.status)
        collectorJob.cancel()
    }

    @Test
    fun `retirer le contenu vide la fiche`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR))
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(mediaId, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRemoveMedia()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.media)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir une fiche suivie charge son synopsis`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1))
        val mediaId = (addResult as Resource.Success).data
        val synopsisRepository = FakeSynopsisRepository().apply { response = Resource.Success("Paul Atréides...") }

        val viewModel = viewModelFor(mediaId, repository, synopsisRepository = synopsisRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Paul Atréides...", viewModel.uiState.value.synopsis)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir un apercu charge aussi son synopsis`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val synopsisRepository = FakeSynopsisRepository().apply { response = Resource.Success("Un employé découpe son esprit...") }
        val viewModel = previewViewModelFor(repository, tmdbId = 42, synopsisRepository = synopsisRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Un employé découpe son esprit...", viewModel.uiState.value.synopsis)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir une fiche suivie charge ses plateformes de streaming`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1))
        val mediaId = (addResult as Resource.Success).data
        val netflix = WatchAvailability.Known(
            streaming = listOf(WatchProvider(id = 8, name = "Netflix", logoUrl = null)),
            rentOrBuy = emptyList(),
            link = null,
        )
        val watchProvidersRepository = FakeWatchProvidersRepository().apply { response = Resource.Success(netflix) }

        val viewModel = viewModelFor(mediaId, repository, watchProvidersRepository = watchProvidersRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(netflix, viewModel.uiState.value.watchAvailability)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir un apercu charge aussi ses plateformes de streaming`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val availability = WatchAvailability.Known(
            streaming = listOf(WatchProvider(id = 350, name = "Apple TV+", logoUrl = null)),
            rentOrBuy = emptyList(),
            link = null,
        )
        val watchProvidersRepository = FakeWatchProvidersRepository().apply { response = Resource.Success(availability) }

        val viewModel = previewViewModelFor(repository, tmdbId = 42, watchProvidersRepository = watchProvidersRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(availability, viewModel.uiState.value.watchAvailability)
        collectorJob.cancel()
    }

    @Test
    fun `un echec de chargement des plateformes vaut disponibilite inconnue`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1))
        val mediaId = (addResult as Resource.Success).data
        val watchProvidersRepository = FakeWatchProvidersRepository().apply { response = Resource.Error("Réseau indisponible") }

        val viewModel = viewModelFor(mediaId, repository, watchProvidersRepository = watchProvidersRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WatchAvailability.Unknown, viewModel.uiState.value.watchAvailability)
        collectorJob.cancel()
    }

    @Test
    fun `un FILM n'a pas de section episodes`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 1),
        )
        val mediaId = (addResult as Resource.Success).data

        val viewModel = viewModelFor(mediaId, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.seasons.isEmpty())
        assertTrue(viewModel.uiState.value.episodes.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir une serie charge les saisons et selectionne la premiere saison reguliere`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(
                listOf(
                    Season(seasonNumber = 0, name = "Spéciaux", episodeCount = 1, posterUrl = null),
                    Season(seasonNumber = 1, name = "Saison 1", episodeCount = 9, posterUrl = null),
                ),
            )
            episodesResponseBySeason = mapOf(
                1 to Resource.Success(listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null))),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.seasons.size)
        assertEquals(1, state.selectedSeasonNumber)
        assertEquals(1, state.episodes.size)
        assertEquals("Bon travail", state.episodes.first().title)
        assertEquals(false, state.episodes.first().watched)
        collectorJob.cancel()
    }

    @Test
    fun `marquer un episode vu met a jour son statut dans l'etat`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.episodes.first().watched)
        collectorJob.cancel()
    }

    @Test
    fun `changer de saison recharge les episodes de la saison choisie`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(
                listOf(
                    Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null),
                    Season(seasonNumber = 2, name = "Saison 2", episodeCount = 1, posterUrl = null),
                ),
            )
            episodesResponseBySeason = mapOf(
                1 to Resource.Success(listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "S1E1", stillUrl = null))),
                2 to Resource.Success(listOf(EpisodeInfo(seasonNumber = 2, episodeNumber = 1, title = "S2E1", stillUrl = null))),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSeasonSelected(2)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.selectedSeasonNumber)
        assertEquals("S2E1", state.episodes.first().title)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir un apercu depuis la recherche n'est pas dans le suivi`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val viewModel = previewViewModelFor(repository, tmdbId = 42, title = "Severance", year = "2022")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInBacklog)
        assertEquals("Severance", state.media?.title)
        assertEquals(2022, state.media?.releaseYear)
        assertEquals("", state.media?.id)
        collectorJob.cancel()
    }

    @Test
    fun `un apercu sans tmdbId valide n'a pas de contenu`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val episodeRepository = fakeEpisodeRepository()
        val jellyfinRepository = FakeJellyfinRepository()
        val viewModel = DetailViewModel(
            savedStateHandle = SavedStateHandle(emptyMap()),
            mediaRepository = repository,
            tvDetailsRepository = FakeTvDetailsRepository(),
            episodeRepository = episodeRepository,
            jellyfinRepository = jellyfinRepository,
            synopsisRepository = FakeSynopsisRepository(),
            watchProvidersRepository = FakeWatchProvidersRepository(),
            setEpisodeWatched = SetEpisodeWatchedUseCase(
                repository,
                episodeRepository,
                FakeTvShowInfoRepository(),
                jellyfinRepository,
                TimeSource { 1_790_000_000_000L },
            ),
            tvShowInfoRepository = FakeTvShowInfoRepository(),
            reminderRepository = EpisodeReminderRepositoryImpl(FakeEpisodeReminderDao()),
            timeSource = TimeSource { now },
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.media)
        collectorJob.cancel()
    }

    @Test
    fun `ajouter depuis l'apercu persiste le contenu et bascule dans le suivi`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val viewModel = previewViewModelFor(repository, tmdbId = 42)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddMedia()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isInBacklog)
        assertTrue(state.media?.id?.isNotEmpty() == true)
        assertEquals(1, repository.observeMedia().first().size)
        collectorJob.cancel()
    }

    @Test
    fun `ajouter une serie depuis l'apercu charge la section episodes`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }
        val viewModel = previewViewModelFor(repository, tmdbId = 95396, type = "SERIE", tvDetailsRepository = tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddMedia()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.seasons.size)
        assertEquals(1, state.episodes.size)
        assertEquals("Bon travail", state.episodes.first().title)
        collectorJob.cancel()
    }

    @Test
    fun `marquer un episode vu pousse vers Jellyfin quand une session est active`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }
        val jellyfinRepository = FakeJellyfinRepository().apply {
            setSession(JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"))
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, jellyfinRepository.pushedEpisodes.size)
        val (media, episode, watched) = jellyfinRepository.pushedEpisodes.first()
        assertEquals(mediaId, media.id)
        assertEquals(1, episode.seasonNumber)
        assertEquals(1, episode.episodeNumber)
        assertTrue(watched)
        collectorJob.cancel()
    }

    @Test
    fun `marquer un episode vu ne pousse rien sans session Jellyfin`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 1, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }
        val jellyfinRepository = FakeJellyfinRepository()

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(jellyfinRepository.pushedEpisodes.isEmpty())
        assertTrue(viewModel.uiState.value.episodes.first().watched)
        collectorJob.cancel()
    }

    @Test
    fun `repasser une serie de Vu a En cours devoit ses episodes localement et sur Jellyfin`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Kaamelott", type = MediaType.SERIE, status = WatchStatus.VU, tmdbId = 95396),
        )
        val mediaId = (addResult as Resource.Success).data
        val episodeRepository = fakeEpisodeRepository()
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, 1), watched = true)
        episodeRepository.setEpisodeWatched(mediaId, EpisodeKey(1, 2), watched = true)
        val jellyfinRepository = FakeJellyfinRepository().apply {
            setSession(JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"))
        }

        val viewModel = viewModelFor(mediaId, repository, episodeRepository = episodeRepository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.EN_COURS)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WatchStatus.EN_COURS, viewModel.uiState.value.media?.status)
        assertTrue(episodeRepository.observeWatchedEpisodes(mediaId).first().isEmpty())
        // Démarquage en bloc sur la série, pas un appel par épisode.
        assertEquals(1, jellyfinRepository.unwatchedSeries.size)
        assertEquals(mediaId, jellyfinRepository.unwatchedSeries.first().id)
        assertTrue(jellyfinRepository.pushedEpisodes.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `repasser un film de Vu a A voir devoit sa lecture sur Jellyfin`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, tmdbId = 438631),
        )
        val mediaId = (addResult as Resource.Success).data
        val jellyfinRepository = FakeJellyfinRepository().apply {
            setSession(JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"))
        }

        val viewModel = viewModelFor(mediaId, repository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.A_VOIR)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WatchStatus.A_VOIR, viewModel.uiState.value.media?.status)
        assertEquals(1, jellyfinRepository.pushedMovies.size)
        assertFalse(jellyfinRepository.pushedMovies.first().second)
        collectorJob.cancel()
    }

    @Test
    fun `changer de statut sans passer par Vu ne touche pas Jellyfin`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val addResult = repository.addMedia(
            Media(title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, tmdbId = 438631),
        )
        val mediaId = (addResult as Resource.Success).data
        val jellyfinRepository = FakeJellyfinRepository().apply {
            setSession(JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token"))
        }

        val viewModel = viewModelFor(mediaId, repository, jellyfinRepository = jellyfinRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.EN_COURS)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(jellyfinRepository.pushedMovies.isEmpty())
        collectorJob.cancel()
    }
    @Test
    fun `cocher un episode d'un contenu A voir le passe En cours dans l'etat`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.A_VOIR, tmdbId = 95396),
        ) as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 2, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WatchStatus.EN_COURS, viewModel.uiState.value.media?.status)
        assertEquals(WatchStatus.EN_COURS, repository.observeMediaById(mediaId).first()?.status)
        collectorJob.cancel()
    }

    @Test
    fun `une edition apres avoir coche un episode ne ramene pas l'ancien statut`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val mediaId = (repository.addMedia(
            Media(title = "Severance", type = MediaType.SERIE, status = WatchStatus.A_VOIR, tmdbId = 95396),
        ) as Resource.Success).data
        val tvDetailsRepository = FakeTvDetailsRepository().apply {
            seasonsResponse = Resource.Success(listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 2, posterUrl = null)))
            defaultEpisodesResponse = Resource.Success(
                listOf(EpisodeInfo(seasonNumber = 1, episodeNumber = 1, title = "Bon travail", stillUrl = null)),
            )
        }

        val viewModel = viewModelFor(mediaId, repository, tvDetailsRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onEpisodeWatchedToggled(viewModel.uiState.value.episodes.first())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRatingSelected(4)
        dispatcher.scheduler.advanceUntilIdle()

        val stored = repository.observeMediaById(mediaId).first()
        assertEquals(WatchStatus.EN_COURS, stored?.status)
        assertEquals(4, stored?.rating)
        collectorJob.cancel()
    }

    private fun airingShow(
        tmdbId: Long = 42,
        airDate: String? = "2026-10-15",
        status: TvShowStatus = TvShowStatus.EN_DIFFUSION,
    ) = TvShowInfo(
        tmdbId = tmdbId,
        status = status,
        seasonEpisodeCounts = emptyMap(),
        lastAired = null,
        nextToAir = EpisodeAirInfo(EpisodeKey(2, 5), AirDate.parse(airDate), title = null),
    )

    private suspend fun trackedSeries(repository: MediaRepository, type: MediaType = MediaType.SERIE): String =
        (repository.addMedia(Media(title = "Severance", type = type, status = WatchStatus.EN_COURS, tmdbId = 42)) as Resource.Success).data

    @Test
    fun `une serie en diffusion expose sa prochaine diffusion`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = trackedSeries(repository)
        val tvShows = FakeTvShowInfoRepository(listOf(airingShow()))
        val viewModel = viewModelFor(id, repository, tvShowInfoRepository = tvShows)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val airing = viewModel.uiState.value.nextAiring
        assertEquals(AirDate.parse("2026-10-15"), airing?.airDate)
        assertEquals(EpisodeKey(2, 5), airing?.key)
        assertEquals(listOf(42L), tvShows.refreshedShows)
        collectorJob.cancel()
    }

    @Test
    fun `une serie terminee n'expose aucune prochaine diffusion`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = trackedSeries(repository)
        val viewModel = viewModelFor(id, repository, tvShowInfoRepository = FakeTvShowInfoRepository(listOf(airingShow(status = TvShowStatus.TERMINEE))))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.nextAiring)
        collectorJob.cancel()
    }

    @Test
    fun `sans date connue, aucune prochaine diffusion`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = trackedSeries(repository)
        val viewModel = viewModelFor(id, repository, tvShowInfoRepository = FakeTvShowInfoRepository(listOf(airingShow(airDate = null))))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.nextAiring)
        collectorJob.cancel()
    }

    @Test
    fun `un film n'interroge pas le cache des series`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = trackedSeries(repository, type = MediaType.FILM)
        val tvShows = FakeTvShowInfoRepository(listOf(airingShow()))
        val viewModel = viewModelFor(id, repository, tvShowInfoRepository = tvShows)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.nextAiring)
        assertTrue(tvShows.refreshedShows.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `l'apercu d'une serie en diffusion montre la prochaine diffusion sans etre suivi`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val viewModel = previewViewModelFor(repository, tmdbId = 42, tvShowInfoRepository = FakeTvShowInfoRepository(listOf(airingShow())))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInBacklog)
        assertEquals(AirDate.parse("2026-10-15"), state.nextAiring?.airDate)
        collectorJob.cancel()
    }

    @Test
    fun `le rappel est desactive par defaut puis suit l'interrupteur`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val id = trackedSeries(repository)
        val viewModel = viewModelFor(id, repository, tvShowInfoRepository = FakeTvShowInfoRepository(listOf(airingShow())))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.reminderEnabled)

        viewModel.onReminderToggled(true)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.reminderEnabled)

        viewModel.onReminderToggled(false)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.reminderEnabled)
        collectorJob.cancel()
    }

    @Test
    fun `activer un rappel depuis l'apercu est ignore`() = runTest {
        val repository = fakeMediaRepository(FakeMediaDao())
        val reminders = EpisodeReminderRepositoryImpl(FakeEpisodeReminderDao())
        val viewModel = previewViewModelFor(repository, reminderRepository = reminders)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onReminderToggled(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertTrue(reminders.getEnabledReminders().isEmpty())
        collectorJob.cancel()
    }

    private fun millisUtc(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private val clock = object : TimeSource {
        override fun nowMillis() = now
        override val zone = ZoneOffset.UTC
    }

    /** Insère directement en Room pour maîtriser `watchedAt` (le repository le dérive à l'écriture). */
    private suspend fun seed(dao: FakeMediaDao, status: WatchStatus, watchedAt: Long?, releaseYear: Int? = 2023): String {
        val media = Media(
            id = "media-1",
            title = "Dune",
            type = MediaType.FILM,
            status = status,
            releaseYear = releaseYear,
            watchedAt = watchedAt,
        )
        dao.insert(media.toEntity())
        return media.id
    }

    @Test
    fun `un contenu Vu affiche son annee de visionnage et les annees proposables`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.VU, watchedAt = millisUtc(2025, 3, 1), releaseYear = 2023)

        val viewModel = viewModelFor(id, fakeMediaRepository(dao, timeSource = clock))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditWatchedYear)
        assertEquals(2025, state.watchedYear)
        assertEquals(listOf(2026, 2025, 2024, 2023), state.watchedYearChoices)
        collectorJob.cancel()
    }

    @Test
    fun `un contenu Vu sans date a une annee inconnue mais reste modifiable`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.VU, watchedAt = null)

        val viewModel = viewModelFor(id, fakeMediaRepository(dao, timeSource = clock))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canEditWatchedYear)
        assertNull(viewModel.uiState.value.watchedYear)
        collectorJob.cancel()
    }

    @Test
    fun `la ligne d'annee est absente hors statut Vu`() = runTest {
        listOf(WatchStatus.A_VOIR, WatchStatus.EN_COURS).forEach { status ->
            val dao = FakeMediaDao()
            val id = seed(dao, status, watchedAt = null)

            val viewModel = viewModelFor(id, fakeMediaRepository(dao, timeSource = clock))
            val collectorJob = launch { viewModel.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse("$status", viewModel.uiState.value.canEditWatchedYear)
            assertTrue("$status", viewModel.uiState.value.watchedYearChoices.isEmpty())
            collectorJob.cancel()
        }
    }

    @Test
    fun `la ligne d'annee est absente sur l'apercu d'un contenu pas encore suivi`() = runTest {
        val viewModel = previewViewModelFor(fakeMediaRepository(FakeMediaDao(), timeSource = clock))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canEditWatchedYear)
        assertTrue(viewModel.uiState.value.watchedYearChoices.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `choisir une annee met a jour l'affichage et la valeur persistee`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.VU, watchedAt = millisUtc(2026, 3, 1))
        val repository = fakeMediaRepository(dao, timeSource = clock)

        val viewModel = viewModelFor(id, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onWatchedYearSelected(2024)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2024, viewModel.uiState.value.watchedYear)
        assertEquals(millisUtc(2024, 7, 1), repository.observeMediaById(id).first()?.watchedAt)
        collectorJob.cancel()
    }

    @Test
    fun `une annee donnee a un contenu sans date s'affiche`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.VU, watchedAt = null)

        val viewModel = viewModelFor(id, fakeMediaRepository(dao, timeSource = clock))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onWatchedYearSelected(2025)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2025, viewModel.uiState.value.watchedYear)
        collectorJob.cancel()
    }

    @Test
    fun `une edition de la note apres le choix d'une annee ne ramene pas l'ancienne valeur`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.VU, watchedAt = millisUtc(2026, 3, 1))
        val repository = fakeMediaRepository(dao, timeSource = clock)

        val viewModel = viewModelFor(id, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onWatchedYearSelected(2024)
        viewModel.onRatingSelected(4)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2024, viewModel.uiState.value.watchedYear)
        assertEquals(millisUtc(2024, 7, 1), repository.observeMediaById(id).first()?.watchedAt)
        assertEquals(4, repository.observeMediaById(id).first()?.rating)
        collectorJob.cancel()
    }

    @Test
    fun `choisir une annee hors statut Vu est ignore`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.EN_COURS, watchedAt = null)
        val repository = fakeMediaRepository(dao, timeSource = clock)

        val viewModel = viewModelFor(id, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onWatchedYearSelected(2024)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(repository.observeMediaById(id).first()?.watchedAt)
        collectorJob.cancel()
    }

    @Test
    fun `passer un contenu a Vu depuis la fiche affiche tout de suite une annee`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.A_VOIR, watchedAt = null)

        val viewModel = viewModelFor(id, fakeMediaRepository(dao, timeSource = clock))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.VU)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canEditWatchedYear)
        assertNotNull(viewModel.uiState.value.watchedYear)
        collectorJob.cancel()
    }

    @Test
    fun `quitter Vu efface l'annee choisie et repasser en Vu ne la retrouve pas`() = runTest {
        val dao = FakeMediaDao()
        val id = seed(dao, WatchStatus.VU, watchedAt = millisUtc(2026, 3, 1))
        val repository = fakeMediaRepository(dao, timeSource = clock)

        val viewModel = viewModelFor(id, repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onWatchedYearSelected(2024)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(WatchStatus.EN_COURS)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canEditWatchedYear)
        assertNull(repository.observeMediaById(id).first()?.watchedAt)

        viewModel.onStatusSelected(WatchStatus.VU)
        dispatcher.scheduler.advanceUntilIdle()
        val year = viewModel.uiState.value.watchedYear
        assertNotNull(year)
        assertTrue(year != 2024)
        collectorJob.cancel()
    }
}
