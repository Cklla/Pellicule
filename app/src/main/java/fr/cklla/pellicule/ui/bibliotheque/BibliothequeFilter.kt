package fr.cklla.pellicule.ui.bibliotheque

import androidx.annotation.StringRes
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.WatchStatus

/** Filtre de statut appliqué à la Bibliothèque ("Tous" en plus des 3 [WatchStatus]). */
enum class BibliothequeFilter(@param:StringRes val labelRes: Int) {
    TOUS(R.string.filter_tous),
    A_VOIR(R.string.filter_a_voir),
    EN_COURS(R.string.filter_en_cours),
    VU(R.string.filter_vu),
}

fun BibliothequeFilter.matches(status: WatchStatus): Boolean = when (this) {
    BibliothequeFilter.TOUS -> true
    BibliothequeFilter.A_VOIR -> status == WatchStatus.A_VOIR
    BibliothequeFilter.EN_COURS -> status == WatchStatus.EN_COURS
    BibliothequeFilter.VU -> status == WatchStatus.VU
}
