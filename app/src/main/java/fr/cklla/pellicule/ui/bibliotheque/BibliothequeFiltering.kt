package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Logique de filtrage/comptage extraite du ViewModel et des Composables pour rester testable en
 * pur Kotlin, sans dépendance Android.
 */

/**
 * Année de visionnage d'un contenu (fuseau horaire de l'appareil), déduite de [Media.watchedAt] —
 * distincte de [Media.releaseYear], l'année de sortie du contenu. `null` tant que le contenu n'est
 * jamais passé par le statut [WatchStatus.VU].
 */
fun watchedYear(media: Media): Int? =
    media.watchedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }

/** Années disponibles pour le filtre "Vu", triées de la plus récente à la plus ancienne. */
fun availableWatchedYears(media: List<Media>): List<Int> =
    media.filter { it.status == WatchStatus.VU }
        .mapNotNull(::watchedYear)
        .distinct()
        .sortedDescending()

/**
 * [watchedYear] ne s'applique qu'au filtre "Vu" : sur les autres filtres, un contenu ne passe pas
 * forcément (encore) par un statut vu, filtrer par année de visionnage n'aurait pas de sens.
 * [selectedType], lui, s'applique sur tous les onglets de statut (Tous/À voir/En cours/Vu).
 */
fun filterMedia(
    media: List<Media>,
    filter: BibliothequeFilter,
    selectedYear: Int? = null,
    selectedType: MediaType? = null,
): List<Media> =
    media.filter { filter.matches(it.status) }
        .filter { filter != BibliothequeFilter.VU || selectedYear == null || watchedYear(it) == selectedYear }
        .filter { selectedType == null || it.type == selectedType }

fun countByFilter(media: List<Media>): Map<BibliothequeFilter, Int> =
    BibliothequeFilter.entries.associateWith { filter -> media.count { filter.matches(it.status) } }
