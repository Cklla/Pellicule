package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media

data class BibliothequeUiState(
    val media: List<Media> = emptyList(),
)
