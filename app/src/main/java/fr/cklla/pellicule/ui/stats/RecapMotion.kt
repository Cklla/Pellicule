package fr.cklla.pellicule.ui.stats

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * Durées, délais et courbes du récap en images, regroupés ici pour s'ajuster d'un seul endroit.
 * Durées et délais en millisecondes ; les délais se mesurent depuis l'entrée de la slide.
 */
internal object RecapMotion {

    /** Courbe commune à toutes les animations : démarrage immédiat, ralentissement progressif. */
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** Amplitude de la montée qui accompagne chaque fondu d'entrée. */
    val RiseOffset = 12.dp

    /**
     * Durée de la ligne de temps d'entrée d'une slide : elle doit couvrir la fin de la dernière
     * animation (délai + durée), vérifié par les tests.
     */
    const val ENTRY_TIMELINE = 1_000

    const val TITLE_DURATION = 320
    const val TITLE_DELAY = 0

    const val KEY_FIGURE_DURATION = 400
    const val KEY_FIGURE_DELAY = 120

    const val COUNT_DURATION = 600
    const val COUNT_DELAY = 120

    const val DETAIL_DURATION = 360
    const val DETAIL_DELAY = 240

    /** Décalage entre deux cartes consécutives d'une liste de détails. */
    const val DETAIL_STAGGER = 80

    const val POSTER_DURATION = 440

    /** Délai des trois premières affiches ; les suivantes reprennent le dernier. */
    val POSTER_DELAYS = intArrayOf(240, 320, 400)

    const val RING_DURATION = 600
    const val RING_DELAY = 120

    /** Fondu du bouton de fermeture et de l'indicateur de progression. */
    const val CHROME_FADE_DURATION = 200
    const val CHROME_FADE_DELAY = 400

    /** Remplissage du segment actif de l'indicateur de progression. */
    const val SEGMENT_FILL_DURATION = 600

    /** Dérive unique du halo violet à l'entrée de la slide ; le grain, lui, ne bouge pas. */
    val HALO_DRIFT = 6.dp
    const val HALO_DRIFT_DURATION = 600
    const val HALO_DRIFT_DELAY = 0

    /** Intensité, position verticale (part de la hauteur) et rayon (part de la largeur) du halo. */
    const val HALO_ALPHA = 0.28f
    const val HALO_CENTER_Y = 0.6f
    const val HALO_RADIUS = 0.9f

    /**
     * Durée de l'accrochage du pager sur la slide entrante. La parallaxe du fond suit la position du
     * pager, elle dure donc autant. La sortie de la slide précédente est menée par le même mouvement.
     */
    const val PAGE_SNAP_DURATION = 400

    /** Part du déplacement de la slide que parcourt le fond pendant la transition. */
    const val PARALLAX_FACTOR = 0.4f

    /** Opacité maximale d'un grain de bruit ; le bruit est dessiné une seule fois, sans animation. */
    const val GRAIN_ALPHA = 0.06f
    const val GRAIN_SEED = 7
    const val GRAIN_TILE_PX = 128

    /** Délai de l'affiche de rang [index] (à partir de 0). */
    fun posterDelay(index: Int): Int = POSTER_DELAYS[index.coerceIn(0, POSTER_DELAYS.lastIndex)]
}
