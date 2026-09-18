package fr.cklla.pellicule.ui.theme

import androidx.compose.ui.graphics.Color

// Thème sombre uniquement.

val BackgroundDark = Color(0xFF0A0812)
val SurfaceCard = Color(0xFF130F24)
val SurfaceCardPressed = Color(0xFF1C1638)

val AccentPurple = Color(0xFF7C4DFF)
val AccentPurpleLight = Color(0xFFA374F7)
val AccentPurpleMuted = Color(0xFF5A3D9E)

val BorderHairline = Color(0xFF3D2D6A)

val TextPrimary = Color(0xFFEDE8FF)
val TextSecondary = Color(0xFFC9B8F0)
val TextTertiary = Color(0xFF9B87C8)
val TextMuted = Color(0xFF8A75C0)

val SuccessGreen = Color(0xFF5DCAA5)
val ErrorCoral = Color(0xFFF09595)

// Dégradés de jaquette placeholder, utilisés tant qu'un contenu n'a pas d'affiche TMDB connue.
val CoverGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFF3D2D6A) to Color(0xFF1C1638),
    Color(0xFF5A3D9E) to Color(0xFF2A1F4A),
    Color(0xFF7C4DFF) to Color(0xFF2A1F4A),
    Color(0xFF4A3670) to Color(0xFF130F24),
    Color(0xFF6B4FA8) to Color(0xFF1C1638),
    Color(0xFF432E75) to Color(0xFF0A0812),
)
