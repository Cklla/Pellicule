package fr.cklla.pellicule.ui.detail

import fr.cklla.pellicule.domain.model.Media

data class DetailUiState(
    val isLoading: Boolean = true,
    val media: Media? = null,
)
