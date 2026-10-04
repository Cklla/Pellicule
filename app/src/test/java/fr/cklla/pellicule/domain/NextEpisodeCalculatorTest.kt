package fr.cklla.pellicule.domain

import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeDetail
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.NextEpisode
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeCalculatorTest {

    private val today = AirDate.parse("2026-10-01")!!

    private fun show(
        counts: Map<Int, Int> = mapOf(1 to 3, 2 to 2),
        status: TvShowStatus = TvShowStatus.TERMINEE,
        lastAired: EpisodeAirInfo? = null,
        nextToAir: EpisodeAirInfo? = null,
        episodes: Map<EpisodeKey, EpisodeDetail> = emptyMap(),
    ) = TvShowInfo(
        tmdbId = 1,
        status = status,
        seasonEpisodeCounts = counts,
        lastAired = lastAired,
        nextToAir = nextToAir,
        episodes = episodes,
    )

    private fun key(season: Int, episode: Int) = EpisodeKey(season, episode)

    @Test
    fun `rien de vu propose le premier episode`() {
        val next = computeNextEpisode(show(), emptySet(), today)

        assertEquals(NextEpisode.Available(key(1, 1), title = null), next)
    }

    @Test
    fun `propose l'episode qui suit le dernier vu dans la saison`() {
        val next = computeNextEpisode(show(), setOf(key(1, 1)), today)

        assertEquals(NextEpisode.Available(key(1, 2), title = null), next)
    }

    @Test
    fun `fin de saison passe au premier episode de la saison suivante`() {
        val next = computeNextEpisode(show(), setOf(key(1, 1), key(1, 2), key(1, 3)), today)

        assertEquals(NextEpisode.Available(key(2, 1), title = null), next)
    }

    @Test
    fun `une saison vide est sautee`() {
        val next = computeNextEpisode(show(counts = mapOf(1 to 1, 2 to 0, 3 to 4)), setOf(key(1, 1)), today)

        assertEquals(NextEpisode.Available(key(3, 1), title = null), next)
    }

    @Test
    fun `un trou dans l'historique n'est pas propose, on repart apres le plus avance`() {
        val next = computeNextEpisode(show(), setOf(key(1, 1), key(1, 3)), today)

        assertEquals(NextEpisode.Available(key(2, 1), title = null), next)
    }

    @Test
    fun `serie commencee a la saison 2 propose la suite de la saison 2`() {
        val next = computeNextEpisode(show(), setOf(key(2, 1)), today)

        assertEquals(NextEpisode.Available(key(2, 2), title = null), next)
    }

    @Test
    fun `tout vu sur une serie terminee vaut termine`() {
        val watched = setOf(key(1, 1), key(1, 2), key(1, 3), key(2, 1), key(2, 2))

        assertEquals(NextEpisode.Completed, computeNextEpisode(show(), watched, today))
    }

    @Test
    fun `tout vu sur une serie annulee vaut termine`() {
        val watched = setOf(key(1, 3), key(2, 2))

        assertEquals(
            NextEpisode.Completed,
            computeNextEpisode(show(status = TvShowStatus.ANNULEE), watched, today),
        )
    }

    @Test
    fun `tout vu sur une serie en diffusion sans suite annoncee vaut a jour`() {
        val watched = setOf(key(2, 2))

        assertEquals(
            NextEpisode.UpToDate,
            computeNextEpisode(show(status = TvShowStatus.EN_DIFFUSION), watched, today),
        )
    }

    @Test
    fun `la saison 0 est ignoree dans les episodes proposes`() {
        val next = computeNextEpisode(show(counts = mapOf(0 to 5, 1 to 2)), emptySet(), today)

        assertEquals(NextEpisode.Available(key(1, 1), title = null), next)
    }

    @Test
    fun `un special coche n'avance pas la position`() {
        val next = computeNextEpisode(show(counts = mapOf(0 to 5, 1 to 2)), setOf(key(0, 3)), today)

        assertEquals(NextEpisode.Available(key(1, 1), title = null), next)
    }

    @Test
    fun `une serie avec seulement des speciaux n'a rien a proposer`() {
        val next = computeNextEpisode(show(counts = mapOf(0 to 3)), emptySet(), today)

        assertEquals(NextEpisode.Completed, next)
    }

    @Test
    fun `episode annonce dans le futur n'est pas propose et expose sa date`() {
        val show = show(
            status = TvShowStatus.EN_DIFFUSION,
            counts = mapOf(1 to 3),
            lastAired = EpisodeAirInfo(key(1, 2), AirDate.parse("2026-09-24"), "Deux"),
            nextToAir = EpisodeAirInfo(key(1, 3), AirDate.parse("2026-10-08"), "Trois"),
        )

        val next = computeNextEpisode(show, setOf(key(1, 1), key(1, 2)), today)

        assertEquals(NextEpisode.Upcoming(key(1, 3), "Trois", AirDate.parse("2026-10-08")), next)
    }

    @Test
    fun `episode annonce sans date reste a venir sans date`() {
        val show = show(
            status = TvShowStatus.EN_DIFFUSION,
            counts = mapOf(1 to 3),
            lastAired = EpisodeAirInfo(key(1, 2), AirDate.parse("2026-09-24"), null),
            nextToAir = null,
        )

        val next = computeNextEpisode(show, setOf(key(1, 1), key(1, 2)), today)

        assertEquals(NextEpisode.Upcoming(key(1, 3), title = null, airDate = null), next)
    }

    @Test
    fun `episode du jour est considere comme diffuse`() {
        val show = show(
            status = TvShowStatus.EN_DIFFUSION,
            counts = mapOf(1 to 3),
            nextToAir = EpisodeAirInfo(key(1, 3), AirDate.parse("2026-10-01"), "Trois"),
        )

        val next = computeNextEpisode(show, setOf(key(1, 2)), today)

        assertEquals(NextEpisode.Available(key(1, 3), "Trois"), next)
    }

    @Test
    fun `un prochain episode annonce hors du decompte de saisons est propose`() {
        val show = show(
            status = TvShowStatus.EN_DIFFUSION,
            counts = mapOf(1 to 2),
            nextToAir = EpisodeAirInfo(key(2, 1), AirDate.parse("2026-12-01"), null),
        )

        val next = computeNextEpisode(show, setOf(key(1, 2)), today)

        assertEquals(NextEpisode.Upcoming(key(2, 1), title = null, airDate = AirDate.parse("2026-12-01")), next)
    }

    @Test
    fun `la date du detail de saison prime sur celle du prochain episode`() {
        val show = show(
            status = TvShowStatus.EN_DIFFUSION,
            counts = mapOf(1 to 3),
            nextToAir = EpisodeAirInfo(key(1, 3), AirDate.parse("2026-10-20"), null),
            episodes = mapOf(key(1, 3) to EpisodeDetail("Trois", AirDate.parse("2026-09-30"))),
        )

        val next = computeNextEpisode(show, setOf(key(1, 2)), today)

        assertEquals(NextEpisode.Available(key(1, 3), "Trois"), next)
    }

    @Test
    fun `le titre vient du detail de saison quand il est charge`() {
        val show = show(episodes = mapOf(key(1, 2) to EpisodeDetail("Le départ", AirDate.parse("2020-01-01"))))

        val next = computeNextEpisode(show, setOf(key(1, 1)), today)

        assertEquals(NextEpisode.Available(key(1, 2), "Le départ"), next)
    }

    @Test
    fun `sans metadonnees en cache le prochain episode est inconnu`() {
        assertEquals(NextEpisode.Unknown, computeNextEpisode(null, setOf(key(1, 1)), today))
    }

    @Test
    fun `statut - premier coche passe A voir a En cours`() {
        assertEquals(
            WatchStatus.EN_COURS,
            statusAfterEpisodeChange(WatchStatus.A_VOIR, watched = true, next = NextEpisode.Available(key(1, 2), null)),
        )
    }

    @Test
    fun `statut - dernier episode d'une serie terminee passe a Vu`() {
        assertEquals(
            WatchStatus.VU,
            statusAfterEpisodeChange(WatchStatus.EN_COURS, watched = true, next = NextEpisode.Completed),
        )
    }

    @Test
    fun `statut - une serie encore diffusee reste En cours meme a jour`() {
        assertEquals(
            WatchStatus.EN_COURS,
            statusAfterEpisodeChange(WatchStatus.EN_COURS, watched = true, next = NextEpisode.UpToDate),
        )
    }

    @Test
    fun `statut - metadonnees inconnues n'autorisent pas le passage a Vu`() {
        assertEquals(
            WatchStatus.EN_COURS,
            statusAfterEpisodeChange(WatchStatus.A_VOIR, watched = true, next = NextEpisode.Unknown),
        )
    }

    @Test
    fun `statut - cocher un episode d'une nouvelle saison repasse un contenu Vu a En cours`() {
        assertEquals(
            WatchStatus.EN_COURS,
            statusAfterEpisodeChange(WatchStatus.VU, watched = true, next = NextEpisode.Available(key(2, 2), null)),
        )
    }

    @Test
    fun `statut - cocher ne retrograde pas un contenu Vu quand il ne reste rien de diffuse apres`() {
        listOf(
            NextEpisode.Completed,
            NextEpisode.UpToDate,
            NextEpisode.Upcoming(key(2, 1), null, null),
            NextEpisode.Unknown,
        ).forEach { next ->
            assertEquals(WatchStatus.VU, statusAfterEpisodeChange(WatchStatus.VU, watched = true, next = next))
        }
    }

    @Test
    fun `episode apres le dernier vu - une saison ajoutee apres la derniere vue est detectee`() {
        val season1 = (1..3).map { key(1, it) }.toSet()
        val server = season1 + setOf(key(2, 1), key(2, 2))

        assertTrue(hasEpisodeAfterLastWatched(server, watched = season1))
    }

    @Test
    fun `episode apres le dernier vu - rien apres l'historique n'est pas une nouveaute`() {
        val season1 = (1..3).map { key(1, it) }.toSet()

        assertFalse(hasEpisodeAfterLastWatched(season1, watched = season1))
    }

    @Test
    fun `episode apres le dernier vu - sans aucun episode vu il n'y a pas de dernier`() {
        assertFalse(hasEpisodeAfterLastWatched(setOf(key(1, 1), key(1, 2)), watched = emptySet()))
    }

    @Test
    fun `episode apres le dernier vu - la saison 0 est ignoree des deux cotes`() {
        assertFalse(hasEpisodeAfterLastWatched(setOf(key(1, 1), key(0, 1)), watched = setOf(key(1, 1))))
        assertFalse(hasEpisodeAfterLastWatched(setOf(key(0, 1), key(0, 2)), watched = setOf(key(0, 1))))
    }

    @Test
    fun `episode apres le dernier vu - un trou avant le dernier vu n'est pas une nouveaute`() {
        assertFalse(hasEpisodeAfterLastWatched(setOf(key(1, 1), key(1, 2), key(1, 3)), watched = setOf(key(1, 1), key(1, 3))))
    }

    @Test
    fun `statut apres pull - un Vu ne regresse pas sans nouveaute`() {
        assertEquals(WatchStatus.VU, statusAfterJellyfinPull(WatchStatus.VU, WatchStatus.A_VOIR, hasNewEpisodes = false))
        assertEquals(WatchStatus.VU, statusAfterJellyfinPull(WatchStatus.VU, WatchStatus.EN_COURS, hasNewEpisodes = false))
    }

    @Test
    fun `statut apres pull - un Vu avec nouveaux episodes repasse En cours`() {
        assertEquals(WatchStatus.EN_COURS, statusAfterJellyfinPull(WatchStatus.VU, WatchStatus.EN_COURS, hasNewEpisodes = true))
        assertEquals(WatchStatus.EN_COURS, statusAfterJellyfinPull(WatchStatus.VU, WatchStatus.A_VOIR, hasNewEpisodes = true))
    }

    @Test
    fun `statut apres pull - les autres statuts ne font que progresser`() {
        assertEquals(WatchStatus.EN_COURS, statusAfterJellyfinPull(WatchStatus.A_VOIR, WatchStatus.EN_COURS, hasNewEpisodes = true))
        assertEquals(WatchStatus.VU, statusAfterJellyfinPull(WatchStatus.EN_COURS, WatchStatus.VU, hasNewEpisodes = false))
        assertEquals(WatchStatus.EN_COURS, statusAfterJellyfinPull(WatchStatus.EN_COURS, WatchStatus.A_VOIR, hasNewEpisodes = true))
    }

    @Test
    fun `statut - decocher repasse un contenu Vu a En cours`() {
        assertEquals(
            WatchStatus.EN_COURS,
            statusAfterEpisodeChange(WatchStatus.VU, watched = false, next = NextEpisode.Available(key(2, 2), null)),
        )
    }

    @Test
    fun `statut - decocher laisse A voir et En cours inchanges`() {
        assertEquals(WatchStatus.A_VOIR, statusAfterEpisodeChange(WatchStatus.A_VOIR, false, NextEpisode.Unknown))
        assertEquals(WatchStatus.EN_COURS, statusAfterEpisodeChange(WatchStatus.EN_COURS, false, NextEpisode.Unknown))
    }
}
