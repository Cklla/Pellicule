package fr.cklla.pellicule.ui

import androidx.annotation.StringRes
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.MediaType

@StringRes
fun MediaType.labelRes(): Int = when (this) {
    MediaType.FILM -> R.string.media_type_film
    MediaType.SERIE -> R.string.media_type_serie
    MediaType.ANIME -> R.string.media_type_anime
}
