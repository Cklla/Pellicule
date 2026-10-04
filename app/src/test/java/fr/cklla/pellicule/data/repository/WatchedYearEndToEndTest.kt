package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.util.TimeSource
import fr.cklla.pellicule.ui.bibliotheque.BibliothequeFilter
import fr.cklla.pellicule.ui.bibliotheque.availableWatchedYears
import fr.cklla.pellicule.ui.bibliotheque.filterMedia
import fr.cklla.pellicule.ui.stats.computeStats
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Chemin complet : `setWatchedYear` écrit dans Room, puis les mêmes fonctions que la Bibliothèque
 * (filtre, années disponibles) et l'écran Statistiques relisent l'année de ce contenu.
 */
class WatchedYearEndToEndTest {

    private val originalZone = TimeZone.getDefault()
    private val zone = ZoneId.of("Europe/Paris")

    // 2026-10-04 à midi, heure de Paris.
    private val clock = object : TimeSource {
        override fun nowMillis() = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        override val zone: ZoneId = this@WatchedYearEndToEndTest.zone
    }

    @Before
    fun setUp() {
        // `watchedYear()` lit le fuseau de l'appareil : on aligne celui de la JVM sur l'horloge.
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `un contenu reclasse apparait sous la nouvelle annee dans la Bibliotheque et les statistiques`() = runTest {
        // Semé en Room avec l'horloge du test : `addMedia` horodate avec l'heure réelle de la machine.
        val dao = FakeMediaDao()
        val vu = Media(title = "", type = MediaType.FILM, status = WatchStatus.VU, watchedAt = clock.nowMillis())
        dao.insert(vu.copy(id = "dune", title = "Dune").toEntity())
        dao.insert(vu.copy(id = "arcane", title = "Arcane", type = MediaType.SERIE).toEntity())
        val repository = fakeMediaRepository(dao, timeSource = clock)
        assertEquals(listOf(2026), availableWatchedYears(repository.observeMedia().first()))

        repository.setWatchedYear("dune", 2024)

        val media = repository.observeMedia().first()
        assertEquals(listOf(2026, 2024), availableWatchedYears(media))
        assertEquals(listOf("Dune"), filterMedia(media, BibliothequeFilter.VU, selectedYear = 2024).map { it.title })
        assertEquals(listOf("Arcane"), filterMedia(media, BibliothequeFilter.VU, selectedYear = 2026).map { it.title })

        val stats2024 = computeStats(media, selectedYear = 2024)
        assertEquals(1, stats2024.watchedCount)
        assertEquals(1, stats2024.countsByType.getValue(MediaType.FILM))
        assertEquals(0, stats2024.countsByType.getValue(MediaType.SERIE))
        val stats2026 = computeStats(media, selectedYear = 2026)
        assertEquals(1, stats2026.watchedCount)
        assertEquals(1, stats2026.countsByType.getValue(MediaType.SERIE))
        assertEquals(2, computeStats(media).watchedCount)
    }

    @Test
    fun `un contenu Vu sans annee recoit une annee et sort du seul total`() = runTest {
        val dao = FakeMediaDao()
        dao.insert(Media(id = "ancien", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU).toEntity())
        val repository = fakeMediaRepository(dao, timeSource = clock)
        assertEquals(emptyList<Int>(), availableWatchedYears(repository.observeMedia().first()))

        repository.setWatchedYear("ancien", 2025)

        val media = repository.observeMedia().first()
        assertEquals(listOf(2025), availableWatchedYears(media))
        assertEquals(1, computeStats(media, selectedYear = 2025).watchedCount)
        assertEquals(1, computeStats(media, selectedYear = 2025).countsByType.getValue(MediaType.ANIME))
    }
}
