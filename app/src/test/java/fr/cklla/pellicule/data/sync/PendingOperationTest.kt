package fr.cklla.pellicule.data.sync

import fr.cklla.pellicule.data.local.entity.PendingOperationEntity
import fr.cklla.pellicule.data.repository.FakeOutboxDao
import fr.cklla.pellicule.domain.model.EpisodeKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOperationTest {

    private val outbox = FakeOutboxDao()

    @Test
    fun `les episodes d'une operation font l'aller-retour, numeros superieurs a 1000 compris`() = runTest {
        val episodes = setOf(EpisodeKey(1, 1), EpisodeKey(12, 1243), EpisodeKey(0, 3))

        outbox.enqueueEpisodes(PendingOperationKind.ADD_EPISODES, "m", episodes)

        assertEquals(episodes, outbox.pending.single().episodeKeys())
    }

    @Test
    fun `une entree illisible est ignoree sans invalider les autres`() {
        val operation = PendingOperationEntity(kind = "ADD_EPISODES", mediaId = "m", episodes = "1:2,abc,3,4:x,5:6:7,2:3")

        assertEquals(setOf(EpisodeKey(1, 2), EpisodeKey(2, 3)), operation.episodeKeys())
    }

    @Test
    fun `une operation sans episodes n'en a aucun`() {
        assertTrue(PendingOperationEntity(kind = "UPSERT_MEDIA", mediaId = "m").episodeKeys().isEmpty())
    }

    @Test
    fun `enqueueEpisodes n'enfile rien pour un ensemble vide`() = runTest {
        outbox.enqueueEpisodes(PendingOperationKind.REMOVE_EPISODES, "m", emptySet())

        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `la nature d'une operation inconnue vaut null`() {
        assertNull(PendingOperationEntity(kind = "AUTRE", mediaId = "m").operationKind)
        assertEquals(PendingOperationKind.DELETE_MEDIA, PendingOperationEntity(kind = "DELETE_MEDIA", mediaId = "m").operationKind)
    }

    @Test
    fun `les operations gardent leur ordre d'ajout`() = runTest {
        outbox.enqueueUpsert("a")
        outbox.enqueueEpisodes(PendingOperationKind.ADD_EPISODES, "a", setOf(EpisodeKey(1, 1)))
        outbox.enqueueDelete("b")

        assertEquals(
            listOf(PendingOperationKind.UPSERT_MEDIA, PendingOperationKind.ADD_EPISODES, PendingOperationKind.DELETE_MEDIA),
            outbox.getAll().map { it.operationKind },
        )
    }

    @Test
    fun `enqueueDelete retire les operations en attente du meme contenu seulement`() = runTest {
        outbox.enqueueUpsert("a")
        outbox.enqueueEpisodes(PendingOperationKind.ADD_EPISODES, "a", setOf(EpisodeKey(1, 1)))
        outbox.enqueueUpsert("b")

        outbox.enqueueDelete("a")

        assertEquals(
            listOf("b" to PendingOperationKind.UPSERT_MEDIA, "a" to PendingOperationKind.DELETE_MEDIA),
            outbox.getAll().map { it.mediaId to it.operationKind },
        )
    }
}
