package fr.cklla.pellicule.ui.stats

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import fr.cklla.pellicule.ui.theme.TextPrimary
import kotlin.random.Random

/**
 * Pixels d'une tuile de bruit carrée de [tileSize] pixels de côté : la teinte de [color] avec une
 * opacité tirée au hasard par pixel. La graine fixe rend la tuile identique à chaque lancement.
 */
internal fun grainPixels(tileSize: Int, seed: Int, color: Color): IntArray {
    val random = Random(seed)
    val rgb = color.toArgb() and 0x00FFFFFF
    return IntArray(tileSize * tileSize) { (random.nextInt(256) shl 24) or rgb }
}

/** Tuile construite une seule fois pour tout le processus, partagée par toutes les slides. */
private val grainTile: ImageBitmap by lazy {
    val size = RecapMotion.GRAIN_TILE_PX
    Bitmap.createBitmap(grainPixels(size, RecapMotion.GRAIN_SEED, TextPrimary), size, size, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/**
 * Grain fixe d'une slide : la tuile de bruit répétée sur toute la surface, à très faible opacité.
 * Le calque est mis en cache et rien ne l'invalide ensuite : il n'est dessiné qu'une fois, avec ou
 * sans réduction des animations.
 */
@Composable
internal fun RecapGrain(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer()
            .drawWithCache {
                val brush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
                onDrawBehind { drawRect(brush, alpha = RecapMotion.GRAIN_ALPHA) }
            },
    )
}
