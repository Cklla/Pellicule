package fr.cklla.pellicule.domain.calendar

/**
 * Grille d'un mois pour un calendrier dont la semaine commence le lundi : [weeks] contient des
 * lignes de 7 cases, chacune étant un numéro de jour ou `null` pour une case vide (jours du mois
 * précédent avant le 1er, jours du mois suivant après le dernier).
 */
data class MonthGrid(val year: Int, val month: Int, val weeks: List<List<Int?>>)

/**
 * Construit la grille du [month] (1 à 12) de l'[year]. Calcul purement arithmétique, sans
 * `java.time` ni `Calendar` : indépendant du fuseau et de la locale de l'appareil.
 */
fun buildMonthGrid(year: Int, month: Int): MonthGrid {
    require(month in 1..12) { "Mois invalide : $month" }
    val leadingBlanks = dayOfWeekMondayFirst(year, month, 1)
    val cells = List(leadingBlanks) { null } + (1..daysInMonth(year, month)).toList()
    val weeks = cells.chunked(DAYS_PER_WEEK).map { week -> week + List(DAYS_PER_WEEK - week.size) { null } }
    return MonthGrid(year, month, weeks)
}

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

/** Jour de la semaine, 0 pour lundi jusqu'à 6 pour dimanche (algorithme de Sakamoto). */
internal fun dayOfWeekMondayFirst(year: Int, month: Int, day: Int): Int {
    val monthOffsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    val sundayFirst = (y + y / 4 - y / 100 + y / 400 + monthOffsets[month - 1] + day) % DAYS_PER_WEEK
    return (sundayFirst + DAYS_PER_WEEK - 1) % DAYS_PER_WEEK
}

private const val DAYS_PER_WEEK = 7
