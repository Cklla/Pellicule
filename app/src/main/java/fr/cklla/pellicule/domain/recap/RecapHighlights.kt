package fr.cklla.pellicule.domain.recap

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.bibliotheque.watchedYear
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Logique pure du récap en images : sélection des coups de cœur, dates approximatives, faits de
 * l'année et liste des slides. Rien ici ne dépend d'Android ni de l'horloge : le fuseau est un
 * paramètre (celui de `TimeSource` à l'appel).
 */

/** Plafond de coups de cœur par type : au-delà, on garde les contenus 5 étoiles vus le plus récemment. */
const val MAX_FAVORITES = 10

/** Nombre minimal de coups de cœur visé par type : en dessous, on complète avec des notes de 4. */
const val MIN_FAVORITES = 3

/** Au plus ce nombre de coups de cœur, la grille passe à 2 colonnes pour garder des affiches lisibles. */
const val FAVORITES_TWO_COLUMNS_MAX = 4

/** Colonnes de la grille des coups de cœur selon leur nombre. */
fun favoritesGridColumns(count: Int): Int = if (count <= FAVORITES_TWO_COLUMNS_MAX) 2 else 3

/** Contenus au statut Vu d'une année, tous types confondus. */
private fun watchedIn(media: List<Media>, year: Int): List<Media> =
    media.filter { it.status == WatchStatus.VU && watchedYear(it) == year }

private val mostRecentlyWatchedFirst: Comparator<Media> =
    compareByDescending<Media> { it.watchedAt ?: Long.MIN_VALUE }.thenBy { it.id }

private val chronological: Comparator<Media> =
    compareBy<Media> { it.watchedAt ?: Long.MAX_VALUE }.thenBy { it.title }.thenBy { it.id }

/**
 * Coups de cœur d'un type pour une année. Seuls comptent les contenus Vu de ce type dont l'année de
 * visionnage est [year] ; les notes `null` sont ignorées.
 *
 * - tous les contenus notés 5, du plus récemment vu au plus ancien, plafonnés à [MAX_FAVORITES] ;
 * - s'il y en a moins de [MIN_FAVORITES], complétés par des contenus notés 4 (même ordre) jusqu'à
 *   en avoir [MIN_FAVORITES], les 5 étoiles restant devant ;
 * - liste vide si aucun contenu n'est noté 4 ou 5.
 */
fun selectFavorites(media: List<Media>, year: Int, type: MediaType): List<Media> {
    val rated = watchedIn(media, year).filter { it.type == type && it.rating != null }
    val fives = rated.filter { it.rating == 5 }.sortedWith(mostRecentlyWatchedFirst).take(MAX_FAVORITES)
    if (fives.size >= MIN_FAVORITES) return fives
    val fours = rated.filter { it.rating == 4 }.sortedWith(mostRecentlyWatchedFirst)
    return fives + fours.take(MIN_FAVORITES - fives.size)
}

/**
 * Vrai si [watchedAt] est exactement le 1er juillet à 12:00:00.000 dans [zone] : l'horodatage posé
 * quand l'année de visionnage est choisie à la main (`watchedAtForYear`), donc sans jour ni mois réels.
 */
fun isApproximateWatchedAt(watchedAt: Long, zone: ZoneId): Boolean {
    val dateTime = Instant.ofEpochMilli(watchedAt).atZone(zone)
    return dateTime.monthValue == Month.JULY.value &&
        dateTime.dayOfMonth == 1 &&
        dateTime.toLocalTime() == LocalTime.NOON
}

/** Un contenu et le jour où il a été vu. */
data class DatedMedia(val media: Media, val date: LocalDate)

/** Mois le plus chargé de l'année et le nombre de contenus vus ce mois-là. */
data class BusiestMonth(val month: Month, val count: Int)

/**
 * Faits de l'année ; chacun vaut `null` quand la donnée n'existe pas.
 *
 * @param first premier contenu vu de l'année (date exacte uniquement).
 * @param last dernier contenu vu ; `null` s'il n'y a qu'un contenu à date exacte, pour ne pas
 *   présenter le même contenu comme premier et dernier.
 * @param busiestMonth mois qui compte le plus de visionnages (date exacte uniquement).
 * @param oldest contenu vu dont l'année de sortie est la plus ancienne (dates approximatives incluses).
 */
data class RecapFacts(
    val first: DatedMedia? = null,
    val last: DatedMedia? = null,
    val busiestMonth: BusiestMonth? = null,
    val oldest: Media? = null,
) {
    val isEmpty: Boolean get() = first == null && last == null && busiestMonth == null && oldest == null
}

/**
 * Faits de l'année [year]. Les contenus dont l'année a été choisie à la main ([isApproximateWatchedAt])
 * sont exclus du premier/dernier et du mois le plus chargé, car leur jour est fictif ; ils comptent
 * pour le contenu le plus ancien, qui ne dépend que de l'année de sortie.
 */
fun computeRecapFacts(media: List<Media>, year: Int, zone: ZoneId): RecapFacts {
    val watched = watchedIn(media, year)
    val exact = watched
        .filter { it.watchedAt != null && !isApproximateWatchedAt(it.watchedAt, zone) }
        .sortedWith(chronological)

    val first = exact.firstOrNull()?.let { it.datedIn(zone) }
    val last = exact.lastOrNull()?.takeIf { it.id != first?.media?.id }?.let { it.datedIn(zone) }

    val busiestMonth = exact
        .groupingBy { Instant.ofEpochMilli(checkNotNull(it.watchedAt)).atZone(zone).month }
        .eachCount()
        .entries
        .maxWithOrNull(compareBy<Map.Entry<Month, Int>> { it.value }.thenBy { it.key.value })
        ?.let { BusiestMonth(it.key, it.value) }

    val oldest = watched
        .filter { it.releaseYear != null }
        .sortedWith(compareBy<Media> { it.releaseYear }.thenComparing(mostRecentlyWatchedFirst))
        .firstOrNull()

    return RecapFacts(first = first, last = last, busiestMonth = busiestMonth, oldest = oldest)
}

private fun Media.datedIn(zone: ZoneId): DatedMedia =
    DatedMedia(this, Instant.ofEpochMilli(checkNotNull(watchedAt)).atZone(zone).toLocalDate())

/** Tous les contenus vus de l'année, tous types confondus, du plus ancien visionnage au plus récent. */
fun mosaicMedia(media: List<Media>, year: Int): List<Media> =
    watchedIn(media, year).sortedWith(chronological)

/** Une slide du récap en images. [key] est stable : il identifie la page du pager entre deux recompositions. */
sealed interface RecapSlide {
    val key: String

    /** Total de l'année et répartition par type (tous les types, éventuellement à 0). */
    data class Total(val year: Int, val total: Int, val countsByType: Map<MediaType, Int>) : RecapSlide {
        override val key: String = "total"
    }

    /** Coups de cœur d'un type, avec le nombre de contenus vus de ce type dans l'année. */
    data class TypeFavorites(val type: MediaType, val watchedCount: Int, val favorites: List<Media>) : RecapSlide {
        override val key: String = "type-${type.name}"
        val columns: Int get() = favoritesGridColumns(favorites.size)
    }

    data class Facts(val facts: RecapFacts) : RecapSlide {
        override val key: String = "facts"
    }

    /** Mosaïque finale : [media] est dans l'ordre chronologique des visionnages. */
    data class Mosaic(val media: List<Media>) : RecapSlide {
        override val key: String = "mosaic"
    }
}

/**
 * Slides du récap de [year], dans l'ordre : total, un slide par type qui a des coups de cœur, faits
 * de l'année, mosaïque. Une slide sans rien à montrer est omise ; sans aucun contenu vu, la liste
 * est vide.
 */
fun buildRecapSlides(media: List<Media>, year: Int, zone: ZoneId): List<RecapSlide> {
    val watched = watchedIn(media, year)
    if (watched.isEmpty()) return emptyList()
    val countsByType = MediaType.entries.associateWith { type -> watched.count { it.type == type } }
    val facts = computeRecapFacts(media, year, zone)
    return buildList {
        add(RecapSlide.Total(year = year, total = watched.size, countsByType = countsByType))
        MediaType.entries.forEach { type ->
            val favorites = selectFavorites(media, year, type)
            if (favorites.isNotEmpty()) {
                add(RecapSlide.TypeFavorites(type, watchedCount = countsByType.getValue(type), favorites = favorites))
            }
        }
        if (!facts.isEmpty) add(RecapSlide.Facts(facts))
        add(RecapSlide.Mosaic(mosaicMedia(media, year)))
    }
}

private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/** « 12 janvier », avec « 1er » pour le premier du mois. */
fun formatRecapDate(date: LocalDate): String =
    if (date.dayOfMonth == 1) "1er ${date.month.frenchName()}" else date.format(DAY_AND_MONTH)

/** Nom du mois en français, en minuscules (« janvier »). */
fun Month.frenchName(): String =
    getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH)
