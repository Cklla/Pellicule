package fr.cklla.pellicule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.cklla.pellicule.ui.theme.CoverGradients
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.TextPrimary

/**
 * Affiche d'un contenu : la vraie image TMDB ([posterUrl]) si elle est connue, sinon un dégradé
 * placeholder (choisi selon le titre, pour une couleur stable par contenu) avec la première
 * lettre du titre en filigrane — même logique que `GameCoverPlaceholder` de Cartouche avant
 * branchement de l'API.
 *
 * [width]/[height] fixent une taille précise (carte liste) ; laissés à `null`, la taille suit le
 * [modifier] fourni par l'appelant.
 */
@Composable
fun MediaCoverPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    posterUrl: String? = null,
    letterStyle: TextStyle = PelliculeTextStyles.coverLetter,
    letterAlpha: Float = 0.16f,
) {
    val shapedModifier = modifier
        .let { if (width != null && height != null) it.size(width, height) else it }
        .clip(RoundedCornerShape(6.dp))

    if (posterUrl != null) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = shapedModifier,
        )
        return
    }

    val gradient = remember(title) {
        CoverGradients[(title.hashCode().mod(CoverGradients.size))]
    }
    Box(
        modifier = shapedModifier.background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "?",
            style = letterStyle,
            color = TextPrimary.copy(alpha = letterAlpha),
        )
    }
}
