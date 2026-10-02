package fr.cklla.pellicule.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date de diffusion sans heure ni fuseau, au format ISO `yyyy-MM-dd` tel que TMDB la renvoie. Les
 * dates ISO se comparent correctement dans l'ordre lexicographique, ce qui évite `java.time`
 * (absent avant l'API 26 sans désucrage, alors que l'application vise l'API 24).
 */
@JvmInline
value class AirDate private constructor(val iso: String) : Comparable<AirDate> {

    override fun compareTo(other: AirDate): Int = iso.compareTo(other.iso)

    val year: Int get() = iso.substring(0, 4).toInt()

    /** Mois de 1 (janvier) à 12. */
    val month: Int get() = iso.substring(5, 7).toInt()

    val day: Int get() = iso.substring(8, 10).toInt()

    /** Date au format affichable (ex. « 12 oct. »), d'après le [locale] fourni. */
    fun format(locale: Locale, timeZone: TimeZone = TimeZone.getDefault()): String {
        val parsed = SimpleDateFormat(PATTERN, Locale.ROOT).apply { this.timeZone = timeZone }.parse(iso) ?: return iso
        return SimpleDateFormat("d MMM", locale).apply { this.timeZone = timeZone }.format(parsed)
    }

    /** Date complète affichable (ex. « jeudi 8 octobre »), d'après le [locale] fourni. */
    fun formatLong(locale: Locale, timeZone: TimeZone = TimeZone.getDefault()): String {
        val parsed = SimpleDateFormat(PATTERN, Locale.ROOT).apply { this.timeZone = timeZone }.parse(iso) ?: return iso
        return SimpleDateFormat("EEEE d MMMM", locale).apply { this.timeZone = timeZone }.format(parsed)
    }

    companion object {
        private const val PATTERN = "yyyy-MM-dd"
        private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        /** `null` si [value] est absent, vide ou n'a pas la forme `yyyy-MM-dd` (TMDB renvoie parfois une chaîne vide). */
        fun parse(value: String?): AirDate? = value?.takeIf { ISO_DATE.matches(it) }?.let(::AirDate)

        /** Jour civil de [millis] dans [timeZone] (celui de l'appareil par défaut). */
        fun fromMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): AirDate =
            AirDate(SimpleDateFormat(PATTERN, Locale.ROOT).apply { this.timeZone = timeZone }.format(Date(millis)))
    }
}
