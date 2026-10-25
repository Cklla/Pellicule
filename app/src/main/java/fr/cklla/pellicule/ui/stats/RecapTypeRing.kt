package fr.cklla.pellicule.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.color
import java.util.Locale

private val RING_STROKE = 23.dp

/**
 * Part de [segment] déjà dessinée quand l'anneau est révélé à hauteur de [revealProgress] (0 à 1) :
 * le balayage va de 0° à 360° depuis le haut, dans le sens horaire, et chaque segment apparaît à
 * mesure que le balayage l'atteint.
 */
internal fun revealedSweep(segment: DonutSegment, revealProgress: Float): Float {
    val sweptAngle = 360f * revealProgress
    val segmentOffset = segment.startAngle + 90f
    return (sweptAngle - segmentOffset).coerceIn(0f, segment.sweepAngle)
}

/**
 * Anneau de répartition par type du récap : même tracé que celui des Statistiques, révélé par un
 * balayage circulaire à l'entrée de la slide ; le total, au centre, compte de 0 à [total].
 */
@Composable
internal fun TypeRing(
    countsByType: Map<MediaType, Int>,
    total: Int,
    clock: RecapEntryClock,
    modifier: Modifier = Modifier,
    ringSize: Dp = 150.dp,
) {
    val segments = donutSegments(countsByType)
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = RING_STROKE.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            val arcSize = Size(diameter, diameter)
            if (segments.isEmpty()) {
                drawArc(
                    color = BorderHairline,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx),
                )
            } else {
                val reveal = clock.progress(RecapMotion.RING_DELAY, RecapMotion.RING_DURATION)
                segments.forEach { segment ->
                    val sweep = revealedSweep(segment, reveal)
                    if (sweep > 0f) {
                        drawArc(
                            color = segment.type.color(),
                            startAngle = segment.startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx),
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUpText(
                target = total,
                clock = clock,
                style = PelliculeTextStyles.donutValue,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.stats_donut_label).uppercase(Locale.FRENCH),
                style = PelliculeTextStyles.donutLabel,
                color = TextMuted,
            )
        }
    }
}
