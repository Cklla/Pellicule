package fr.cklla.pellicule.ui.theme

import androidx.compose.ui.graphics.Color
import fr.cklla.pellicule.domain.model.MediaType

/** Couleur associée à un type de contenu dans les statistiques. */
fun MediaType.color(): Color = when (this) {
    MediaType.FILM -> TypeFilm
    MediaType.SERIE -> TypeSerie
    MediaType.ANIME -> TypeAnime
}
