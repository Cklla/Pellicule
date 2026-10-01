package fr.cklla.pellicule.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.model.toMedia
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.JellyfinRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.SynopsisRepository
import fr.cklla.pellicule.domain.repository.TvDetailsRepository
import fr.cklla.pellicule.domain.repository.WatchProvidersRepository
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le contenu affiché est chargé une fois depuis le repository, puis conservé dans une copie de
 * travail locale ([workingMedia]) que chaque action met à jour avant de persister (fire-and-forget)
 * via le repository — évite qu'un aller-retour Room en cours n'écrase un changement de statut fait
 * juste après.
 *
 * Deux façons d'arriver sur cette fiche : depuis la Bibliothèque, avec l'id d'un contenu déjà
 * suivi ([mediaId] renseigné) ; ou directement depuis un résultat de Recherche pas encore ajouté,
 * auquel cas le résultat TMDB transite par les autres arguments de route ([previewResult]) et
 * [workingMedia] démarre avec un id vide. Tant que l'id est vide, la fiche est en aperçu
 * (`DetailUiState.isInBacklog` vaut `false`) : statut/retrait/épisodes restent masqués et aucune
 * édition n'est persistée (voir [applyEdit]) jusqu'à ce que [onAddMedia] l'ajoute réellement.
 *
 * Section épisodes (SERIE/ANIME suivi uniquement, voir [isInBacklog]) : les saisons et la liste
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
    private val jellyfinRepository: JellyfinRepository,
    private val synopsisRepository: SynopsisRepository,
    private val watchProvidersRepository: WatchProvidersRepository,
) : ViewModel() {

    private val mediaId: String? = savedStateHandle[PelliculeDestinations.DETAIL_ARG_MEDIA_ID]
    private val previewResult: MediaSearchResult? = if (mediaId != null) null else savedStateHandle.toPreviewResult()

    private val workingMedia = MutableStateFlow(previewResult?.toMedia())
    private val isLoading = MutableStateFlow(mediaId != null)
    private val synopsis = MutableStateFlow<String?>(null)
    private val watchAvailability = MutableStateFlow<WatchAvailability?>(null)

    private val seasons = MutableStateFlow<List<Season>>(emptyList())
    private val selectedSeasonNumber = MutableStateFlow<Int?>(null)
    private val episodesResult = MutableStateFlow<Resource<List<EpisodeInfo>>>(Resource.Success(emptyList()))
    private val episodesLoading = MutableStateFlow(false)

    init {
        val id = mediaId
        if (id != null) {
            viewModelScope.launch {
                val media = mediaRepository.observeMediaById(id).first()
                workingMedia.value = media
                isLoading.value = false
                loadSeasonsIfApplicable()
                media?.let {
                    fetchSynopsis(it)
                    fetchWatchAvailability(it)
                }
            }
        } else {
            workingMedia.value?.let {
                fetchSynopsis(it)
                fetchWatchAvailability(it)
            }
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
        // Pas encore ajoutée au suivi (fiche en aperçu) : pas d'id local pour rattacher un
        // éventuel statut vu/non-vu, donc pas de section épisodes tant que ce n'est pas le cas
        // (voir `onAddMedia`, qui relance ce chargement une fois l'ajout fait).
        if (media.id.isEmpty() || media.type == MediaType.FILM) return

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

    /** Synopsis TMDB, jamais mis en cache localement : rechargé à chaque ouverture de la fiche. */
    private fun fetchSynopsis(media: Media) {
        val tmdbId = media.tmdbId ?: return
        viewModelScope.launch {
            val result = synopsisRepository.getSynopsis(tmdbId, media.type)
            if (result is Resource.Success) synopsis.value = result.data
        }
    }

    /**
     * Plateformes françaises, jamais mises en cache : les offres changent trop souvent. Un appel
     * en échec vaut disponibilité inconnue — c'est bien ce qu'on sait à cet instant, et l'afficher
     * ainsi vaut mieux qu'annoncer à tort une absence d'offre.
     */
    private fun fetchWatchAvailability(media: Media) {
        val tmdbId = media.tmdbId ?: return
        viewModelScope.launch {
            val result = watchProvidersRepository.getAvailability(tmdbId, media.type)
            watchAvailability.value = (result as? Resource.Success)?.data ?: WatchAvailability.Unknown
        }
    }

    private data class EpisodesSectionState(
        val episodes: List<EpisodeUiModel> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val watchedEpisodes = workingMedia
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id.isNullOrEmpty()) flowOf(emptySet()) else episodeRepository.observeWatchedEpisodes(id) }

    private val episodesSection = combine(
        episodesResult,
        episodesLoading,
        watchedEpisodes,
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

    private data class MediaSectionState(
        val media: Media? = null,
        val synopsis: String? = null,
        val watchAvailability: WatchAvailability? = null,
    )

    // `combine` n'a pas d'overload à 7 flux typés : tout ce qui décrit le contenu lui-même est
    // regroupé en amont pour rester dans la limite des 5 arguments du `combine` final.
    private val mediaSection = combine(workingMedia, synopsis, watchAvailability, ::MediaSectionState)

    val uiState: StateFlow<DetailUiState> = combine(
        isLoading,
        mediaSection,
        seasons,
        selectedSeasonNumber,
        episodesSection,
    ) { loading, mediaState, seasonList, selectedSeason, episodesState ->
        DetailUiState(
            isLoading = loading,
            media = mediaState.media,
            synopsis = mediaState.synopsis,
            watchAvailability = mediaState.watchAvailability,
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

    // Repasser un contenu "Vu" à un autre statut doit aussi démarquer ce qu'il efface côté
    // Jellyfin (épisodes ou film) — sinon le prochain pull recalcule "Vu" depuis Jellyfin (qui
    // n'a pas bougé) et réimpose immédiatement l'ancien statut, voir `JellyfinRepository`.
    fun onStatusSelected(status: WatchStatus) {
        val previousStatus = workingMedia.value?.status
        applyEdit { it.copy(status = status) }
        if (previousStatus == WatchStatus.VU && status != WatchStatus.VU) {
            resetWatchedOnJellyfin()
        }
    }

    private fun resetWatchedOnJellyfin() {
        val media = workingMedia.value?.takeIf { it.id.isNotEmpty() } ?: return
        viewModelScope.launch {
            when (media.type) {
                MediaType.FILM -> jellyfinRepository.pushMovieWatched(media, watched = false)
                // Effacement en bloc (une transaction, un appel réseau) plutôt qu'épisode par
                // épisode : sur une série de plusieurs centaines d'épisodes, une boucle d'appels
                // réseau n'aurait aucune chance d'aboutir avant que l'écran ne soit quitté.
                MediaType.SERIE, MediaType.ANIME -> {
                    episodeRepository.replaceWatchedEpisodes(media.id, emptySet())
                    jellyfinRepository.pushSeriesUnwatched(media)
                }
            }
        }
    }

    // `rating = null` correspond à "aucune note" : cliquer sur l'étoile qui représente déjà la
    // note actuelle (voir `RatingSection`) doit pouvoir revenir à cet état, pas seulement en
    // choisir une nouvelle.
    fun onRatingSelected(rating: Int?) = applyEdit { it.copy(rating = rating) }

    fun onSeasonSelected(seasonNumber: Int) {
        selectedSeasonNumber.value = seasonNumber
    }

    fun onEpisodeWatchedToggled(episode: EpisodeUiModel) {
        val media = workingMedia.value?.takeIf { it.id.isNotEmpty() } ?: return
        val watched = !episode.watched
        viewModelScope.launch {
            episodeRepository.setEpisodeWatched(
                mediaId = media.id,
                episode = EpisodeKey(episode.seasonNumber, episode.episodeNumber),
                watched = watched,
            )
            // Best-effort, silencieux : voir `JellyfinRepository.pushEpisodeWatched` (no-op sans
            // session active, un prochain sync global rattrape un éventuel échec réseau).
            jellyfinRepository.pushEpisodeWatched(media, episode.seasonNumber, episode.episodeNumber, watched)
        }
    }

    /**
     * Ajoute la fiche en aperçu au suivi. La fiche ne navigue nulle part : `workingMedia` reçoit
     * l'id fraîchement généré, ce qui fait basculer `isInBacklog` à `true` et affiche directement
     * statut/retrait/épisodes sur le même écran.
     */
    fun onAddMedia() {
        val current = workingMedia.value ?: return
        if (current.id.isNotEmpty()) return
        viewModelScope.launch {
            val result = mediaRepository.addMedia(current)
            if (result is Resource.Success) {
                workingMedia.value = current.copy(id = result.data)
                loadSeasonsIfApplicable()
            }
        }
    }

    fun onRemoveMedia() {
        val id = workingMedia.value?.id?.takeIf { it.isNotEmpty() } ?: return
        // Retrait optimiste : l'écran revient en arrière dès que `media` devient `null`
        // (voir DetailScreen), sans attendre l'aller-retour Room.
        workingMedia.value = null
        viewModelScope.launch { mediaRepository.deleteMedia(id) }
    }

    private fun applyEdit(transform: (Media) -> Media) {
        val updated = workingMedia.value?.let(transform) ?: return
        workingMedia.value = updated
        // Pas encore ajoutée au suivi : rien à persister, seule la copie de travail locale change
        // (voir `onAddMedia`, qui écrit pour la première fois).
        if (updated.id.isEmpty()) return
        viewModelScope.launch { mediaRepository.updateMedia(updated) }
    }

    private fun SavedStateHandle.toPreviewResult(): MediaSearchResult? {
        val tmdbId = get<Long>(PelliculeDestinations.DETAIL_APERCU_ARG_TMDB_ID)?.takeIf { it > 0 } ?: return null
        val type = get<String>(PelliculeDestinations.DETAIL_APERCU_ARG_TYPE)
            ?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: return null
        return MediaSearchResult(
            tmdbId = tmdbId,
            title = get<String>(PelliculeDestinations.DETAIL_APERCU_ARG_TITLE).orEmpty(),
            type = type,
            year = get<String>(PelliculeDestinations.DETAIL_APERCU_ARG_YEAR)?.toIntOrNull(),
            posterUrl = get<String>(PelliculeDestinations.DETAIL_APERCU_ARG_POSTER_URL)?.takeIf { it.isNotEmpty() },
        )
    }
}
