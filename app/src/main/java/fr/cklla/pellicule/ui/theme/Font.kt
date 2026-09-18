package fr.cklla.pellicule.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import fr.cklla.pellicule.R

// Fraunces et Inter sont distribuées ici sous forme de polices variables (un seul fichier .ttf
// couvrant plusieurs graisses) : on règle l'axe "wght" via FontVariation.Settings plutôt que
// d'embarquer un fichier par graisse. Sous Android < 26, l'axe est ignoré et la police s'affiche
// dans sa graisse par défaut — dégradation acceptable, l'italique reste correct.

@OptIn(ExperimentalTextApi::class)
private fun frauncesItalic(weight: Int, tag: FontWeight) = Font(
    resId = R.font.fraunces_italic_variable,
    weight = tag,
    style = FontStyle.Italic,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.inter_italic_variable else R.font.inter_variable,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Fraunces italique, graisses 500 (kickers) et 600 (titres, gros chiffres). */
val FrauncesItalic = FontFamily(
    frauncesItalic(500, FontWeight.Medium),
    frauncesItalic(600, FontWeight.SemiBold),
)

/** Inter, graisses 400 à 600, romain et italique. */
val InterFamily = FontFamily(
    inter(400),
    inter(500),
    inter(600),
)
