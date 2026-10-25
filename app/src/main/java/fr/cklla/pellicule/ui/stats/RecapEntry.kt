package fr.cklla.pellicule.ui.stats

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/** Vrai quand l'échelle de durée des animations du système est à 0 : plus aucun mouvement. */
internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** Réduction des animations demandée au système ; mise à jour si l'utilisateur la change en cours de route. */
internal val LocalRecapReducedMotion = compositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val reduced = remember(context) { mutableStateOf(readReducedMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = readReducedMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced.value
}

private fun readReducedMotion(context: Context): Boolean =
    isReducedMotion(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))

/** Avancement (0 à 1, courbe appliquée) d'une animation qui débute à [delayMillis] et dure [durationMillis]. */
internal fun entryProgress(timeMillis: Float, delayMillis: Int, durationMillis: Int): Float =
    RecapMotion.EaseOut.transform(((timeMillis - delayMillis) / durationMillis).coerceIn(0f, 1f))

/** Valeur entière affichée par un comptage dont l'avancement est [progress]. */
internal fun countUpValue(target: Int, progress: Float): Int = (target * progress).roundToInt()

/**
 * Horloge d'entrée d'une slide : une seule ligne de temps linéaire dont chaque élément déduit son
 * avancement à partir de son délai et de sa durée. Elle n'est lue que dans des blocs de dessin ou
 * des `derivedStateOf`, si bien qu'une animation ne provoque aucune recomposition de la slide.
 */
@Stable
internal class RecapEntryClock private constructor(initialMillis: Float) {
    private val time = Animatable(initialMillis)

    fun progress(delayMillis: Int, durationMillis: Int): Float = entryProgress(time.value, delayMillis, durationMillis)

    suspend fun play() {
        time.animateTo(
            targetValue = RecapMotion.ENTRY_TIMELINE.toFloat(),
            animationSpec = tween(durationMillis = RecapMotion.ENTRY_TIMELINE, easing = LinearEasing),
        )
    }

    companion object {
        /** Horloge terminée : tout est affiché à l'état final, sans mouvement. */
        val Completed = RecapEntryClock(RecapMotion.ENTRY_TIMELINE.toFloat())

        fun starting() = RecapEntryClock(0f)
    }
}

/** Horloge de la slide en cours de composition ; terminée par défaut (aperçus, tests). */
internal val LocalRecapEntryClock = compositionLocalOf { RecapEntryClock.Completed }

/** Horloge qui démarre à l'entrée de la slide, ou déjà terminée si les animations sont réduites. */
@Composable
internal fun rememberRecapEntryClock(): RecapEntryClock {
    val reducedMotion = LocalRecapReducedMotion.current
    val clock = remember { if (reducedMotion) RecapEntryClock.Completed else RecapEntryClock.starting() }
    LaunchedEffect(clock) { if (!reducedMotion) clock.play() }
    return clock
}

/** Fondu et montée d'un élément, calés sur l'horloge d'entrée de la slide. */
internal fun Modifier.recapEnter(clock: RecapEntryClock, delayMillis: Int, durationMillis: Int): Modifier =
    graphicsLayer {
        val progress = clock.progress(delayMillis, durationMillis)
        alpha = progress
        translationY = (1f - progress) * RecapMotion.RiseOffset.toPx()
    }

/**
 * Nombre qui compte de 0 à [target]. La place est réservée à la valeur finale (texte invisible,
 * seul lu par l'accessibilité), pour que la mise en page ne bouge pas pendant le comptage.
 */
@Composable
internal fun CountUpText(
    target: Int,
    clock: RecapEntryClock,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    delayMillis: Int = RecapMotion.COUNT_DELAY,
    durationMillis: Int = RecapMotion.COUNT_DURATION,
) {
    val value by remember(target, clock) {
        derivedStateOf { countUpValue(target, clock.progress(delayMillis, durationMillis)) }
    }
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        Text(text = target.toString(), style = style, color = color, modifier = Modifier.alpha(0f))
        Text(
            text = value.toString(),
            style = style,
            color = color,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
