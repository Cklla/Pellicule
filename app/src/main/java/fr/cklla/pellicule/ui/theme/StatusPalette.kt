package fr.cklla.pellicule.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.WatchStatus

/** Couleur de statut + fond/bordure du badge associé. */
data class StatusPalette(
    val color: Color,
    val badgeBackground: Color,
    val badgeBorder: Color,
)

@StringRes
fun WatchStatus.labelRes(): Int = when (this) {
    WatchStatus.A_VOIR -> R.string.filter_a_voir
    WatchStatus.EN_COURS -> R.string.filter_en_cours
    WatchStatus.VU -> R.string.filter_vu
}

fun WatchStatus.palette(): StatusPalette = when (this) {
    WatchStatus.A_VOIR -> StatusPalette(
        color = TextMuted,
        badgeBackground = TextMuted.copy(alpha = 0.12f),
        badgeBorder = TextMuted.copy(alpha = 0.4f),
    )
    WatchStatus.EN_COURS -> StatusPalette(
        color = AccentPurpleLight,
        badgeBackground = AccentPurple.copy(alpha = 0.16f),
        badgeBorder = AccentPurple.copy(alpha = 0.45f),
    )
    WatchStatus.VU -> StatusPalette(
        color = SuccessGreen,
        badgeBackground = SuccessGreen.copy(alpha = 0.14f),
        badgeBorder = SuccessGreen.copy(alpha = 0.4f),
    )
}
