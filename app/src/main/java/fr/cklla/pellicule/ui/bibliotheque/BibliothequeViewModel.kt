package fr.cklla.pellicule.ui.bibliotheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.computeNextEpisode
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.NextEpisode
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.repository.TvShowInfoRepository
import fr.cklla.pellicule.domain.usecase.SetEpisodeWatchedUseCase
import fr.cklla.pellicule.domain.util.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BibliothequeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val episodeRepository: EpisodeRepository,
    private val tvShowInfoRepository: TvShowInfoRepository,
    private val setEpisodeWatched: SetEpisodeWatchedUseCase,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(BibliothequeFilter.TOUS)
    private val selectedWatchedYear = MutableStateFlow<Int?>(null)
    private val selectedType = MutableStateFlow<MediaType?>(null)

    // Un "+1" par contenu à la fois : le suivant se calcule sur l'état que le précédent vient
    // d'écrire, deux écritures concurrentes viseraient le même épisode.
    private val pendingMediaIds = mutableSetOf<String>()

    private val nextEpisodes: Flow<Map<String, NextEpisode>> = combine(
        mediaRepository.observeMedia(),
        episodeRepository.observeAllWatchedEpisodes(),
        tvShowInfoRepository.observeShows(),
    ) { media, watched, shows ->
        val today = today()
        media.filter { it.hasNextEpisodeCard() }.associate { item ->
            item.id to computeNextEpisode(shows[item.tmdbId], watched[item.id].orEmpty(), today)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<BibliothequeUiState> = combine(
        mediaRepository.observeMedia(),
        selectedFilter,
        selectedWatchedYear,
        selectedType,
        nextEpisodes,
    ) { media, filter, watchedYear, type, nextEpisodes ->
        BibliothequeUiState(
            isLoading = false,
            visibleMedia = filterMedia(media, filter, watchedYear, type),
            selectedFilter = filter,
            filterCounts = countByFilter(media),
            availableWatchedYears = availableWatchedYears(media),
            selectedWatchedYear = watchedYear,
            selectedType = type,
            nextEpisodes = nextEpisodes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliothequeUiState(),
    )

    init {
        keepTrackedShowsFresh()
        loadTitlesOfNextEpisodes()
    }

    // Une seule requête TMDB par série dont le cache est absent ou périmé, jamais une par
    // affichage : le repository ne sollicite le réseau que si la validité est dépassée.
    private fun keepTrackedShowsFresh() {
        viewModelScope.launch {
            mediaRepository.observeMedia()
                .map { media -> media.filter { it.hasNextEpisodeCard() }.mapNotNull { it.tmdbId }.toSet() }
                .distinctUntilChanged()
                .collectLatest { tmdbIds -> tmdbIds.forEach { tvShowInfoRepository.refreshIfStale(it) } }
        }
    }

    // Le titre d'un épisode n'est connu d'emblée que s'il est le dernier diffusé ou le prochain
    // annoncé : sinon on charge le détail de sa seule saison. Ce chargement se déclenche une fois
    // que la fiche de la série est en cache, d'où le recoupement avec `nextEpisodes`.
    private fun loadTitlesOfNextEpisodes() {
        viewModelScope.launch {
            combine(mediaRepository.observeMedia(), nextEpisodes) { media, next ->
                media.mapNotNull { item ->
                    val tmdbId = item.tmdbId ?: return@mapNotNull null
                    val season = when (val episode = next[item.id]) {
                        is NextEpisode.Available -> episode.key.seasonNumber.takeIf { episode.title == null }
                        is NextEpisode.Upcoming -> episode.key.seasonNumber.takeIf { episode.title == null }
                        else -> null
                    } ?: return@mapNotNull null
                    tmdbId to season
                }.toSet()
            }
                .distinctUntilChanged()
                .collectLatest { seasons ->
                    seasons.forEach { (tmdbId, season) -> tvShowInfoRepository.refreshEpisodesIfStale(tmdbId, season) }
                }
        }
    }

    fun onFilterSelected(filter: BibliothequeFilter) {
        selectedFilter.value = filter
        // Le filtre par année n'a de sens que sous "Vu" (voir `filterMedia`) : changer de filtre
        // de statut repart d'une sélection d'année propre plutôt que de garder un choix invisible.
        // Le filtre par type, lui, s'applique sur tous les onglets et reste donc actif.
        selectedWatchedYear.value = null
    }

    /** Re-sélectionner l'année déjà active la désélectionne (retour à "Toutes les années"). */
    fun onWatchedYearSelected(year: Int?) {
        selectedWatchedYear.value = if (year != null && selectedWatchedYear.value == year) null else year
    }

    /** Re-sélectionner le type déjà actif le désélectionne (retour à "Tous les types"). */
    fun onTypeSelected(type: MediaType?) {
        selectedType.value = if (type != null && selectedType.value == type) null else type
    }

    /**
     * Marque comme vu le prochain épisode du contenu, par le même chemin d'écriture que la liste
     * d'épisodes de la fiche Détail. L'épisode visé est recalculé ici sur l'état en base plutôt que
     * lu dans `uiState`, qui peut avoir un temps de retard : des "+1" enchaînés avancent donc
     * toujours d'un épisode chacun.
     */
    fun onWatchNextEpisode(mediaId: String) {
        if (!pendingMediaIds.add(mediaId)) return
        viewModelScope.launch {
            try {
                val next = currentNextEpisode(mediaId) as? NextEpisode.Available ?: return@launch
                setEpisodeWatched(mediaId, next.key, watched = true)
            } finally {
                pendingMediaIds.remove(mediaId)
            }
        }
    }

    private suspend fun currentNextEpisode(mediaId: String): NextEpisode? {
        val media = mediaRepository.observeMedia().first().firstOrNull { it.id == mediaId } ?: return null
        if (!media.hasNextEpisodeCard()) return null
        val watched = episodeRepository.observeWatchedEpisodes(mediaId).first()
        val show = media.tmdbId?.let { tvShowInfoRepository.getCachedShow(it) }
        return computeNextEpisode(show, watched, today())
    }

    private fun today() = AirDate.fromMillis(timeSource.nowMillis())

    private fun Media.hasNextEpisodeCard() =
        status == WatchStatus.EN_COURS && (type == MediaType.SERIE || type == MediaType.ANIME) && tmdbId != null
}
