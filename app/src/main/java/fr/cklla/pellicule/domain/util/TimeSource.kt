package fr.cklla.pellicule.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Horloge injectable, pour figer le temps dans les tests (validité du cache, épisode déjà diffusé). */
fun interface TimeSource {
    fun nowMillis(): Long

    /** Fuseau dans lequel lire et construire les dates : celui de l'appareil, sauf substitution en test. */
    val zone: ZoneId get() = ZoneId.systemDefault()
}

/** Date du jour de cette horloge, dans le fuseau [zone] (celui de l'horloge par défaut). */
fun TimeSource.today(zone: ZoneId = this.zone): LocalDate =
    Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()
