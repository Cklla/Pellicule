package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.firestore.FakeFirestoreMediaDataSource
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinEpisodeDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinUserDataDto
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.JellyfinSession
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pull Jellyfin de bout en bout avec la vraie pile locale : Room (en mémoire), miroir Firestore →
 * Room et écritures Firestore, sans le SDK. Reproduit la sortie d'une nouvelle saison sur un
 * serveur dont la série est déjà vue en entier.
 */
class JellyfinPullEndToEndTest {

    private val session = JellyfinSession(serverUrl = "https://jellyfin.exemple.fr", userId = "user-1", username = "stef", accessToken = "token-abc")

    private val seasonOne = (1..9).map { EpisodeKey(1, it) }.toSet()

    private val arcane = Media(
        id = "arcane",
        title = "Arcane",
        type = MediaType.SERIE,
        status = WatchStatus.VU,
        tmdbId = 94605,
        jellyfinId = "jf-arcane",
        watchedAt = 1_700_000_000_000,
    )

    private fun episode(season: Int, number: Int, played: Boolean) = JellyfinEpisodeDto(
        id = "jf-$season-$number",
        seasonNumber = season,
        episodeNumber = number,
        userData = JellyfinUserDataDto(played = played),
    )

    /** Saison 1 entièrement vue, saison 2 ajoutée au serveur dont seul le premier épisode est lu. */
    private fun serverWithSecondSeasonStarted() = FakeJellyfinApi().apply {
        episodesBySeriesId = mapOf(
            "jf-arcane" to (1..9).map { episode(1, it, played = true) } +
                (1..9).map { episode(2, it, played = it == 1) },
        )
    }

    private class Stack(val firestore: FakeFirestoreMediaDataSource, val episodeDao: FakeEpisodeDao, val mediaRepository: fr.cklla.pellicule.domain.repository.MediaRepository, val episodeRepository: EpisodeRepositoryImpl)

    private fun stack(media: Media, remoteEpisodes: Set<EpisodeKey>): Stack {
        val firestore = FakeFirestoreMediaDataSource()
        val auth = FakeAuthRepository()
        val episodeDao = FakeEpisodeDao()
        firestore.remoteMedia.value = listOf(media)
        firestore.remoteEpisodes.value = mapOf(media.id to remoteEpisodes)
        val mediaRepository = fakeMediaRepository(FakeMediaDao(), firestore, auth, episodeDao = episodeDao)
        val episodeRepository = fakeEpisodeRepository(episodeDao, firestore, auth)
        return Stack(firestore, episodeDao, mediaRepository, episodeRepository)
    }

    @Test
    fun `le premier episode de la saison 2 lu sur Jellyfin se coche et reste coche apres le miroir Firestore`() = runTest {
        val stack = stack(arcane, seasonOne)
        val repository = JellyfinRepositoryImpl(serverWithSecondSeasonStarted(), FakeJellyfinSessionStore(session), stack.mediaRepository, stack.episodeRepository)

        repository.syncTrackedSeries(stack.mediaRepository.observeMedia().first())

        val expected = seasonOne + EpisodeKey(2, 1)
        assertEquals(expected, stack.episodeRepository.observeWatchedEpisodes("arcane").first())
        assertEquals(expected, stack.firestore.remoteEpisodes.value["arcane"])
        val media = stack.mediaRepository.observeMediaById("arcane").first()
        assertEquals(WatchStatus.EN_COURS, media?.status)
        assertEquals(null, media?.watchedAt)
        assertEquals(WatchStatus.EN_COURS, stack.firestore.remoteMedia.value.single().status)
    }
}
