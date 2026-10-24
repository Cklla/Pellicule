package fr.cklla.pellicule.ui.stats

import fr.cklla.pellicule.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapMotionTest {

    @Test
    fun `une animation n'a pas commence avant son delai`() {
        assertEquals(0f, entryProgress(timeMillis = 100f, delayMillis = 120, durationMillis = 400), 0f)
    }

    @Test
    fun `une animation est terminee apres delai et duree`() {
        assertEquals(1f, entryProgress(timeMillis = 520f, delayMillis = 120, durationMillis = 400), 0f)
        assertEquals(1f, entryProgress(timeMillis = 5_000f, delayMillis = 120, durationMillis = 400), 0f)
    }

    @Test
    fun `l'avancement est croissant et ralentit en fin de course`() {
        val samples = (0..10).map { entryProgress(it * 40f, delayMillis = 0, durationMillis = 400) }

        assertEquals(samples.sorted(), samples)
        val firstStep = samples[1] - samples[0]
        val lastStep = samples[10] - samples[9]
        assertTrue("démarre vite : $firstStep", firstStep > lastStep)
    }

    @Test
    fun `la ligne de temps couvre chaque animation d'entree`() {
        val ends = buildList {
            add(RecapMotion.TITLE_DELAY + RecapMotion.TITLE_DURATION)
            add(RecapMotion.KEY_FIGURE_DELAY + RecapMotion.KEY_FIGURE_DURATION)
            add(RecapMotion.COUNT_DELAY + RecapMotion.COUNT_DURATION)
            add(RecapMotion.RING_DELAY + RecapMotion.RING_DURATION)
            add(RecapMotion.DETAIL_DELAY + RecapMotion.DETAIL_DURATION + 3 * RecapMotion.DETAIL_STAGGER)
            repeat(RecapMotion.POSTER_DELAYS.size) { add(RecapMotion.posterDelay(it) + RecapMotion.POSTER_DURATION) }
        }

        assertTrue(ends.max() <= RecapMotion.ENTRY_TIMELINE)
    }

    @Test
    fun `les affiches au-dela de la troisieme reprennent le dernier delai`() {
        assertEquals(240, RecapMotion.posterDelay(0))
        assertEquals(320, RecapMotion.posterDelay(1))
        assertEquals(400, RecapMotion.posterDelay(2))
        assertEquals(400, RecapMotion.posterDelay(9))
    }

    @Test
    fun `le comptage va de 0 au total par entiers`() {
        assertEquals(0, countUpValue(19, 0f))
        assertEquals(10, countUpValue(19, 0.5f))
        assertEquals(19, countUpValue(19, 1f))
    }

    @Test
    fun `seule une echelle d'animation a zero reduit les animations`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
    }

    private val film = DonutSegment(MediaType.FILM, startAngle = -90f, sweepAngle = 270f)
    private val serie = DonutSegment(MediaType.SERIE, startAngle = 180f, sweepAngle = 90f)

    @Test
    fun `l'anneau est vide au depart et complet a la fin`() {
        assertEquals(0f, revealedSweep(film, 0f), 0f)
        assertEquals(270f, revealedSweep(film, 1f), 0f)
        assertEquals(90f, revealedSweep(serie, 1f), 0f)
    }

    @Test
    fun `le balayage revele les segments dans l'ordre`() {
        // 180° balayés sur 360° : le premier segment est à demi dessiné, le suivant pas encore.
        assertEquals(180f, revealedSweep(film, 0.5f), 0.001f)
        assertEquals(0f, revealedSweep(serie, 0.5f), 0f)
        // 0,875 × 360° = 315° : le second segment (270° à 360°) est entamé de 45°.
        assertEquals(45f, revealedSweep(serie, 0.875f), 0.001f)
    }
}
