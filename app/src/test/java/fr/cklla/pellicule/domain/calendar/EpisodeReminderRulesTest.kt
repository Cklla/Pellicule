package fr.cklla.pellicule.domain.calendar

import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeReminderRulesTest {

    private val today = date("2026-10-08")
    private val key = EpisodeKey(2, 5)

    private fun date(iso: String) = AirDate.parse(iso)!!

    private fun episode(airDate: String?, key: EpisodeKey = this.key) =
        EpisodeAirInfo(key, AirDate.parse(airDate), title = null)

    private fun info(
        next: EpisodeAirInfo? = null,
        last: EpisodeAirInfo? = null,
        status: TvShowStatus = TvShowStatus.EN_DIFFUSION,
    ) = TvShowInfo(tmdbId = 42, status = status, seasonEpisodeCounts = emptyMap(), lastAired = last, nextToAir = next)

    @Test
    fun `notifie le jour de la diffusion`() {
        assertTrue(shouldNotify(today, episode("2026-10-08"), TvShowStatus.EN_DIFFUSION, lastNotified = null))
    }

    @Test
    fun `ne notifie pas la veille de la diffusion`() {
        assertFalse(shouldNotify(date("2026-10-07"), episode("2026-10-08"), TvShowStatus.EN_DIFFUSION, null))
    }

    @Test
    fun `ne notifie pas le lendemain de la diffusion`() {
        assertFalse(shouldNotify(date("2026-10-09"), episode("2026-10-08"), TvShowStatus.EN_DIFFUSION, null))
    }

    @Test
    fun `ne renotifie pas un episode deja notifie`() {
        assertFalse(shouldNotify(today, episode("2026-10-08"), TvShowStatus.EN_DIFFUSION, lastNotified = key))
    }

    @Test
    fun `notifie un episode plus recent que le dernier notifie`() {
        assertTrue(shouldNotify(today, episode("2026-10-08"), TvShowStatus.EN_DIFFUSION, lastNotified = EpisodeKey(2, 4)))
        assertTrue(shouldNotify(today, episode("2026-10-08", EpisodeKey(3, 1)), TvShowStatus.EN_DIFFUSION, EpisodeKey(2, 10)))
    }

    @Test
    fun `ne notifie pas un episode plus ancien que le dernier notifie`() {
        assertFalse(shouldNotify(today, episode("2026-10-08"), TvShowStatus.EN_DIFFUSION, lastNotified = EpisodeKey(2, 6)))
    }

    @Test
    fun `ne notifie pas pour une serie terminee ou annulee`() {
        assertFalse(shouldNotify(today, episode("2026-10-08"), TvShowStatus.TERMINEE, null))
        assertFalse(shouldNotify(today, episode("2026-10-08"), TvShowStatus.ANNULEE, null))
    }

    @Test
    fun `ne notifie pas sans episode ni sans date`() {
        assertFalse(shouldNotify(today, null, TvShowStatus.EN_DIFFUSION, null))
        assertFalse(shouldNotify(today, episode(null), TvShowStatus.EN_DIFFUSION, null))
    }

    @Test
    fun `un statut inconnu n'empeche pas la notification`() {
        assertTrue(shouldNotify(today, episode("2026-10-08"), TvShowStatus.INCONNU, null))
    }

    @Test
    fun `episodeToNotify retient l'episode du jour annonce comme prochain`() {
        val result = episodeToNotify(today, info(next = episode("2026-10-08")), lastNotified = null)

        assertEquals(key, result?.key)
    }

    @Test
    fun `episodeToNotify retrouve l'episode du jour deja passe dans les derniers diffuses`() {
        val result = episodeToNotify(
            today,
            info(next = episode("2026-10-15", EpisodeKey(2, 6)), last = episode("2026-10-08")),
            lastNotified = null,
        )

        assertEquals(key, result?.key)
    }

    @Test
    fun `episodeToNotify ne retient rien un autre jour`() {
        assertNull(episodeToNotify(today, info(next = episode("2026-10-15", EpisodeKey(2, 6)), last = episode("2026-10-01", EpisodeKey(2, 4))), null))
    }

    @Test
    fun `episodeToNotify ne revient pas sur un episode deja notifie`() {
        val shows = info(next = episode("2026-10-15", EpisodeKey(2, 6)), last = episode("2026-10-08"))

        assertNull(episodeToNotify(today, shows, lastNotified = key))
    }

    @Test
    fun `upcomingAiring retourne le prochain episode date`() {
        val result = upcomingAiring(today, info(next = episode("2026-10-15")))

        assertEquals(date("2026-10-15"), result?.airDate)
        assertEquals(key, result?.key)
    }

    @Test
    fun `upcomingAiring inclut un episode qui sort aujourd'hui`() {
        assertEquals(today, upcomingAiring(today, info(next = episode("2026-10-08")))?.airDate)
    }

    @Test
    fun `upcomingAiring est vide pour une serie terminee, sans date ou sans donnees`() {
        assertNull(upcomingAiring(today, info(next = episode("2026-10-15"), status = TvShowStatus.TERMINEE)))
        assertNull(upcomingAiring(today, info(next = episode("2026-10-15"), status = TvShowStatus.ANNULEE)))
        assertNull(upcomingAiring(today, info(next = episode(null))))
        assertNull(upcomingAiring(today, info(next = null)))
        assertNull(upcomingAiring(today, null))
    }

    @Test
    fun `upcomingAiring ignore une date deja passee`() {
        assertNull(upcomingAiring(today, info(next = episode("2026-10-07"))))
    }
}
