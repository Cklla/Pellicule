package fr.cklla.pellicule.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Typographie Material3 par défaut, au cas où un composant Material standard s'appuie dessus.
// Les écrans de l'app utilisent leurs propres styles (voir PelliculeTextStyles), calqués sur la
// maquette plutôt que sur cette échelle.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    )
)
