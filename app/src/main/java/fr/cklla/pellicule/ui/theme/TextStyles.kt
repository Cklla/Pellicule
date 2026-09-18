package fr.cklla.pellicule.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Styles de texte "one-off", en dehors de l'échelle Material3 classique. Les couleurs qui
 * dépendent d'un état (actif/inactif, couleur de statut) restent en dehors de ces styles et sont
 * passées au paramètre `color` de `Text` au cas par cas. Non exhaustif : les styles s'ajoutent au
 * fil des écrans.
 */
object PelliculeTextStyles {

    val kicker = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
    )

    val screenTitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
    )

    val chipLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )

    val cardTitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
    )

    val cardSubtitle = TextStyle(
        fontFamily = InterFamily,
        fontSize = 12.sp,
    )

    val badgeLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.5.sp,
    )

    val coverLetter = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 28.sp,
    )

    // Lettre en filigrane des jaquettes en carte liste (Bibliothèque), plus grande que celle des
    // résultats de recherche.
    val coverLetterListCard = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 34.sp,
    )

    val emptyTitle = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 17.sp,
    )

    val emptyMessage = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
    )

    val navLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
    )

    // --- Écran Détail ---

    val coverLetterLarge = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 96.sp,
    )

    val backLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )

    val detailTitle = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 25.sp,
    )

    val detailSubtitle = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
    )

    val sectionLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.9.sp,
    )

    val statusPillLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )

    val linkLabel = TextStyle(
        fontFamily = InterFamily,
        fontSize = 12.5.sp,
    )
}
