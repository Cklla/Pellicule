package fr.cklla.pellicule.ui.stats

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.ui.bibliotheque.BibliothequeFilter
import fr.cklla.pellicule.ui.bibliotheque.filterMedia

/**
 * Statistiques de visionnage d'une sélection (année et/ou type).
 *
 * @param watchedCount contenus vus sur la sélection complète (année **et** type).
 * @param countsByType contenus vus par type sur l'année sélectionnée seule : le type sélectionné
 *   n'en restreint pas la répartition, qui garde ses trois entrées (éventuellement à 0).
 */
data class StatsData(
    val selectedYear: Int? = null,
    val selectedType: MediaType? = null,
    val watchedCount: Int = 0,
    val countsByType: Map<MediaType, Int> = MediaType.entries.associateWith { 0 },
)

/**
 * Calcule les statistiques des contenus au statut Vu. L'année est celle de visionnage
 * ([fr.cklla.pellicule.ui.bibliotheque.watchedYear], fuseau de l'appareil) : un contenu passé en
 * Vu avant l'horodatage des visionnages n'a pas d'année et ne compte donc que sans année
 * sélectionnée. S'appuie sur [filterMedia] pour que les chiffres coïncident toujours avec la liste
 * de la Bibliothèque filtrée de la même façon.
 */
fun computeStats(media: List<Media>, selectedYear: Int? = null, selectedType: MediaType? = null): StatsData {
    val watchedInYear = filterMedia(media, BibliothequeFilter.VU, selectedYear)
    return StatsData(
        selectedYear = selectedYear,
        selectedType = selectedType,
        watchedCount = filterMedia(media, BibliothequeFilter.VU, selectedYear, selectedType).size,
        countsByType = MediaType.entries.associateWith { type -> watchedInYear.count { it.type == type } },
    )
}

/** Portion de l'anneau d'un type : angles en degrés, départ en haut (-90°), sens horaire. */
data class DonutSegment(val type: MediaType, val startAngle: Float, val sweepAngle: Float)

/** Segments de l'anneau, dans l'ordre de [MediaType.entries] ; vide s'il n'y a rien à dessiner. */
fun donutSegments(countsByType: Map<MediaType, Int>): List<DonutSegment> {
    val total = countsByType.values.sum()
    if (total == 0) return emptyList()
    var start = -90f
    return MediaType.entries.mapNotNull { type ->
        val count = countsByType[type] ?: 0
        if (count == 0) return@mapNotNull null
        val sweep = 360f * count / total
        DonutSegment(type, start, sweep).also { start += sweep }
    }
}
