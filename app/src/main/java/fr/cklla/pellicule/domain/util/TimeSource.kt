package fr.cklla.pellicule.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Horloge injectable, pour figer le temps dans les tests (validité du cache, épisode déjà diffusé). */
fun interface TimeSource {
    fun nowMillis(): Long
}

/** Date du jour de cette horloge, dans le fuseau [zone] (celui de l'appareil par défaut). */
fun TimeSource.today(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()
