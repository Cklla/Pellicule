package fr.cklla.pellicule.ui.detail

/**
 * Durée d'un film au format français courant : « 45 min », « 2 h », « 2 h 16 min ». Espaces
 * insécables pour que la durée ne se coupe jamais en fin de ligne sous le titre.
 */
fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}
