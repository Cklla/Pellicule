package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.TvDetailsRepository
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le contenu affiché est chargé une fois depuis le repository, puis conservé dans une copie de
 * travail locale ([workingMedia]) que chaque action met à jour avant de persister (fire-and-forget)
 * via le repository — évite qu'un aller-retour Room en cours n'écrase un changement de statut fait
 * juste après.
 *
 * Section épisodes (SERIE/ANIME uniquement, voir [Media.type]) : les saisons et la liste
 * d'épisodes d'une saison viennent de TMDB (métadonnées, jamais mises en cache localement), tandis
 * que le statut vu/non-vu vient de [EpisodeRepository] (Room) — la jointure des deux se fait dans
 * [episodesSection].
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val tvDetailsRepository: TvDetailsRepository,
    private val episodeRepository: EpisodeRepository,
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle[PelliculeDestinations.DETAIL_ARG_MEDIA_ID])

    private val workingMedia = MutableStateFlow<Media?>(null)
    private val isLoading = MutableStateFlow(true)

    private val seasons = MutableStateFlow<List<Season>>(emptyList())
    private val selectedSeasonNumber = MutableStateFlow<Int?>(null)
    private val episodesResult = MutableStateFlow<Resource<List<EpisodeInfo>>>(Resource.Success(emptyList()))
    private val episodesLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            workingMedia.value = mediaRepository.observeMediaById(mediaId).first()
            isLoading.value = false
            loadSeasonsIfApplicable()
        }

        viewModelScope.launch {
            selectedSeasonNumber.filterNotNull().collectLatest { seasonNumber ->
                val tmdbId = workingMedia.value?.tmdbId ?: return@collectLatest
                episodesLoading.value = true
                episodesResult.value = tvDetailsRepository.getEpisodes(tmdbId, seasonNumber)
                episodesLoading.value = false
            }
        }
    }

    private suspend fun loadSeasonsIfApplicable() {
        val media = workingMedia.value ?: return
        val tmdbId = media.tmdbId ?: return
        if (media.type == MediaType.FILM) return

        when (val result = tvDetailsRepository.getSeasons(tmdbId)) {
            is Resource.Success -> {
                seasons.value = result.data
                // La saison 0 chez TMDB désigne les "spéciaux" : on ouvre plutôt sur la première
                // saison régulière quand elle existe, en repli sur la première saison listée sinon.
                val defaultSeason = result.data.firstOrNull { it.seasonNumber >= 1 } ?: result.data.firstOrNull()
                defaultSeason?.let { selectedSeasonNumber.value = it.seasonNumber }
            }
            is Resource.Error -> episodesResult.value = result
        }
    }

    private data class EpisodesSectionState(
        val episodes: List<EpisodeUiModel> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val episodesSection = combine(
        episodesResult,
        episodesLoading,
        episodeRepository.observeWatchedEpisodes(mediaId),
    ) { result, loading, watched ->
        EpisodesSectionState(
            episodes = (result as? Resource.Success)?.data.orEmpty().map { info ->
                EpisodeUiModel(
                    seasonNumber = info.seasonNumber,
                    episodeNumber = info.episodeNumber,
                    title = info.title,
                    stillUrl = info.stillUrl,
                    watched = EpisodeKey(info.seasonNumber, info.episodeNumber) in watched,
                )
            },
            isLoading = loading,
            errorMessage = (result as? Resource.Error)?.message,
        )
    }

    val uiState: StateFlow<DetailUiState> = combine(
        isLoading,
        workingMedia,
        seasons,
        selectedSeasonNumber,
        episodesSection,
    ) { loading, media, seasonList, selectedSeason, episodesState ->
        DetailUiState(
            isLoading = loading,
            media = media,
            seasons = seasonList,
            selectedSeasonNumber = selectedSeason,
            episodes = episodesState.episodes,
            episodesLoading = episodesState.isLoading,
            episodesErrorMessage = episodesState.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = isLoading.value, media = workingMedia.value),
    )

    fun onStatusSelected(status: WatchStatus) = applyEdit { it.copy(status = status) }

    fun onSeasonSelected(seasonNumber: Int) {
        selectedSeasonNumber.value = seasonNumber
    }

    fun onEpisodeWatchedToggled(episode: EpisodeUiModel) {
        viewModelScope.launch {
            episodeRepository.setEpisodeWatched(
                mediaId = mediaId,
                episode = EpisodeKey(episode.seasonNumber, episode.episodeNumber),
                watched = !episode.watched,
            )
        }
    }

    fun onRemoveMedia() {
        // Retrait optimiste : l'écran revient en arrière dès que `media` devient `null`
        // (voir DetailScreen), sans attendre l'aller-retour Room.
        workingMedia.value = null
        viewModelScope.launch { mediaRepository.deleteMedia(mediaId) }
    }

    private fun applyEdit(transform: (Media) -> Media) {
        val updated = workingMedia.value?.let(transform) ?: return
        workingMedia.value = updated
        viewModelScope.launch { mediaRepository.updateMedia(updated) }
    }
}
