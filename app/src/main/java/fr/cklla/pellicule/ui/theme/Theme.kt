package fr.cklla.pellicule.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Thème sombre uniquement, sur le modèle de Cartouche : pas de variante claire ni de couleur
// dynamique (Material You), qui casseraient l'identité visuelle voulue.
private val PelliculeColorScheme = darkColorScheme(
    primary = AccentPurple,
    secondary = AccentPurpleLight,
    tertiary = SuccessGreen,
    background = BackgroundDark,
    surface = SurfaceCard,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorCoral,
)

@Composable
fun PelliculeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PelliculeColorScheme,
        typography = Typography,
        content = content,
    )
}
