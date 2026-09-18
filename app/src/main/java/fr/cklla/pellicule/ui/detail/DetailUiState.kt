package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.Season

data class DetailUiState(
    val isLoading: Boolean = true,
    val media: Media? = null,
    /** Saisons disponibles pour une série/anime ; toujours vide pour un FILM. */
    val seasons: List<Season> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val episodes: List<EpisodeUiModel> = emptyList(),
    val episodesLoading: Boolean = false,
    val episodesErrorMessage: String? = null,
)
