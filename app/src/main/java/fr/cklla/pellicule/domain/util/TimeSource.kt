package fr.cklla.pellicule.domain.util

/** Horloge injectable, pour figer le temps dans les tests (validité du cache, épisode déjà diffusé). */
fun interface TimeSource {
    fun nowMillis(): Long
}
