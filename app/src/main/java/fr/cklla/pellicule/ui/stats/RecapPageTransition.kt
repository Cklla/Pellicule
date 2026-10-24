package fr.cklla.pellicule.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import fr.cklla.pellicule.ui.theme.BackgroundDark
import kotlin.math.abs

/**
 * Position d'une page par rapport à l'écran, en largeurs de page : 0 quand elle est en place, positive
 * quand elle part vers la gauche, négative quand elle arrive de la droite.
 */
internal fun PagerState.pageOffset(index: Int): Float = (currentPage - index) + currentPageOffsetFraction

/** Opacité d'une page selon sa position : pleine en place, nulle une page plus loin. */
internal fun pageFadeAlpha(pageOffset: Float): Float = (1f - abs(pageOffset)).coerceIn(0f, 1f)

/**
 * Décalage à ajouter au fond d'une page pour qu'il parcoure seulement [RecapMotion.PARALLAX_FACTOR] du
 * déplacement de la page, qui lui est déjà appliqué par le pager.
 */
internal fun parallaxCompensation(pageOffset: Float, pageWidthPx: Float): Float =
    pageOffset * pageWidthPx * (1f - RecapMotion.PARALLAX_FACTOR)

/** Fondu d'une page pendant le glissement ; [pageOffset] n'est lu qu'au dessin, sans recomposition. */
internal fun Modifier.recapPageFade(reducedMotion: Boolean, pageOffset: () -> Float): Modifier =
    if (reducedMotion) this else graphicsLayer { alpha = pageFadeAlpha(pageOffset()) }

/**
 * Fond d'une slide : voile de la couleur [tint] qui se fond dans le fond sombre, halo violet dont
 * la parallaxe suit le glissement du pager et qui dérive une seule fois à l'entrée de la slide, et
 * grain fixe par-dessus.
 */
@Composable
internal fun RecapSlideBackground(
    tint: Color,
    clock: RecapEntryClock,
    reducedMotion: Boolean,
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val veil = Brush.verticalGradient(listOf(tint.copy(alpha = 0.2f), BackgroundDark))
                onDrawBehind { drawRect(veil) }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (reducedMotion) 0f else parallaxCompensation(pageOffset(), size.width)
                }
                .drawWithCache {
                    val halo = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = RecapMotion.HALO_ALPHA), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * RecapMotion.HALO_CENTER_Y),
                        radius = size.width * RecapMotion.HALO_RADIUS,
                    )
                    val driftPx = RecapMotion.HALO_DRIFT.toPx()
                    onDrawBehind {
                        val progress = clock.progress(RecapMotion.HALO_DRIFT_DELAY, RecapMotion.HALO_DRIFT_DURATION)
                        translate(top = (1f - progress) * driftPx) { drawRect(halo) }
                    }
                },
        )
        RecapGrain(modifier = Modifier.fillMaxSize())
    }
}
