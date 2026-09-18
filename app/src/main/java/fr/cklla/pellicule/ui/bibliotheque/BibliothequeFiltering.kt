package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media

/**
 * Logique de filtrage/comptage extraite du ViewModel et des Composables pour rester testable en
 * pur Kotlin, sans dépendance Android.
 */

fun filterMedia(media: List<Media>, filter: BibliothequeFilter): List<Media> =
    media.filter { filter.matches(it.status) }

fun countByFilter(media: List<Media>): Map<BibliothequeFilter, Int> =
    BibliothequeFilter.entries.associateWith { filter -> media.count { filter.matches(it.status) } }
