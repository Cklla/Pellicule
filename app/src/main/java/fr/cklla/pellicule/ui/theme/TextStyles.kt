package fr.cklla.pellicule.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Styles de texte "one-off" repris de Cartouche, en dehors de l'échelle Material3 classique. Les
 * couleurs qui dépendent d'un état (actif/inactif, couleur de statut) restent en dehors de ces
 * styles et sont passées au paramètre `color` de `Text` au cas par cas. Non exhaustif : seuls les
 * styles utilisés par le squelette actuel sont repris, les autres seront ajoutés au fil des
 * écrans (voir WORK.md).
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
}
