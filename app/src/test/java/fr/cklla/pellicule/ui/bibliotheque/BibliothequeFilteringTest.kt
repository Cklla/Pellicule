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

    // Milieu d'année en UTC : hors de portée d'un changement de fuseau horaire local qui ferait
    // basculer la date sur l'année voisine (au plus ±14h autour de l'UTC).
    private val watchedIn2023 = 1_688_169_600_000L // 2023-07-01T00:00:00Z
    private val watchedIn2024 = 1_719_792_000_000L // 2024-07-01T00:00:00Z

    @Test
    fun `watchedYear derive l'annee de visionnage depuis watchedAt`() {
        assertEquals(2023, watchedYear(perfectBlue.copy(watchedAt = watchedIn2023)))
        assertEquals(null, watchedYear(perfectBlue))
    }

    @Test
    fun `availableWatchedYears ne considere que les contenus Vu, sans doublon, du plus recent au plus ancien`() {
        val goT = Media(id = "4", title = "Game of Thrones", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = watchedIn2024)
        val perfectBlueVu2023 = perfectBlue.copy(watchedAt = watchedIn2023)
        val severanceVu2023 = severance.copy(status = WatchStatus.VU, watchedAt = watchedIn2023)

        val years = availableWatchedYears(listOf(perfectBlueVu2023, severanceVu2023, goT, dune))

        assertEquals(listOf(2024, 2023), years)
    }

    @Test
    fun `filtre par annee de visionnage ne garde que les contenus Vu cette annee-la`() {
        val goT = Media(id = "4", title = "Game of Thrones", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = watchedIn2024)
        val perfectBlueVu2023 = perfectBlue.copy(watchedAt = watchedIn2023)

        val result = filterMedia(listOf(perfectBlueVu2023, goT, dune), BibliothequeFilter.VU, selectedYear = 2024)

        assertEquals(listOf(goT), result)
    }

    @Test
    fun `le filtre par annee ne s'applique pas en dehors du filtre Vu`() {
        val duneVu2023 = dune.copy(status = WatchStatus.VU, watchedAt = watchedIn2023)

        // TOUS ignore l'année sélectionnée : un contenu "à voir" sans watchedAt reste visible.
        val result = filterMedia(listOf(duneVu2023, severance), BibliothequeFilter.TOUS, selectedYear = 2024)

        assertEquals(listOf(duneVu2023, severance), result)
    }

    @Test
    fun `filtre par type ne garde que les contenus du type selectionne`() {
        assertEquals(listOf(perfectBlue), filterMedia(media, BibliothequeFilter.TOUS, selectedType = MediaType.ANIME))
        assertEquals(listOf(dune), filterMedia(media, BibliothequeFilter.TOUS, selectedType = MediaType.FILM))
        assertEquals(listOf(severance), filterMedia(media, BibliothequeFilter.TOUS, selectedType = MediaType.SERIE))
    }

    @Test
    fun `filtre par type se combine avec le filtre de statut`() {
        val duneVu = dune.copy(status = WatchStatus.VU)

        val result = filterMedia(listOf(perfectBlue, duneVu, severance), BibliothequeFilter.VU, selectedType = MediaType.FILM)

        assertEquals(listOf(duneVu), result)
    }

    @Test
    fun `filtre par type absent (null) ne restreint rien`() {
        assertEquals(media, filterMedia(media, BibliothequeFilter.TOUS, selectedType = null))
    }
}
