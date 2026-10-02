package fr.cklla.pellicule.domain.recap

import java.time.LocalDate

/**
 * Année ciblée par le récap annuel selon la date du jour sur l'appareil, ou `null` hors fenêtre
 * (aucun récap à proposer). [today] est passé en paramètre plutôt que lu ici, pour rester testable
 * sur chaque cas limite.
 *
 * Deux fenêtres, calées sur le moment où un bilan a du sens :
 * - du 25 au 31 décembre inclus : l'année touche à sa fin, le récap porte sur l'année en cours ;
 * - du 1er au 31 janvier inclus : l'année vient de se terminer, le récap porte sur la précédente.
 */
fun recapTargetYear(today: LocalDate): Int? = when {
    today.monthValue == 12 && today.dayOfMonth >= 25 -> today.year
    today.monthValue == 1 -> today.year - 1
    else -> null
}

/**
 * Année pour laquelle notifier le récap aujourd'hui, ou `null` si rien n'est dû : fenêtre fermée
 * ([recapTargetYear]) ou année déjà notifiée.
 *
 * [alreadyNotified] est un prédicat plutôt qu'un `Set<Int>` pour rester découplé du stockage et
 * testable sans dépendance Android.
 */
fun recapYearToNotify(today: LocalDate, alreadyNotified: (Int) -> Boolean): Int? =
    recapTargetYear(today)?.takeUnless(alreadyNotified)
