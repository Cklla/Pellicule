package fr.cklla.pellicule.ui.bibliotheque

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BibliothequeFilteringTest {

    private val perfectBlue = Media(id = "1", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU)
    private val dune = Media(id = "2", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR)
    private val severance = Media(id = "3", title = "Severance", type = MediaType.SERIE, status = WatchStatus.EN_COURS)
    private val media = listOf(perfectBlue, dune, severance)

    @Test
    fun `filtre TOUS renvoie tous les contenus`() {
        assertEquals(media, filterMedia(media, BibliothequeFilter.TOUS))
    }

    @Test
    fun `filtre par statut ne renvoie que les contenus correspondants`() {
        assertEquals(listOf(perfectBlue), filterMedia(media, BibliothequeFilter.VU))
        assertEquals(listOf(dune), filterMedia(media, BibliothequeFilter.A_VOIR))
        assertEquals(listOf(severance), filterMedia(media, BibliothequeFilter.EN_COURS))
    }

    @Test
    fun `countByFilter compte chaque statut et TOUS compte l'ensemble`() {
        val counts = countByFilter(media)

        assertEquals(3, counts[BibliothequeFilter.TOUS])
        assertEquals(1, counts[BibliothequeFilter.VU])
        assertEquals(1, counts[BibliothequeFilter.A_VOIR])
        assertEquals(1, counts[BibliothequeFilter.EN_COURS])
    }
}
