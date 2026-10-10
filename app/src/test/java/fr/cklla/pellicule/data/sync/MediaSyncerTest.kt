package fr.cklla.pellicule.data.sync

import fr.cklla.pellicule.data.local.entity.PendingOperationEntity
import fr.cklla.pellicule.data.local.entity.WatchedEpisodeEntity
import fr.cklla.pellicule.data.remote.FakeRemoteMediaDataSource
import fr.cklla.pellicule.data.remote.PermanentRemoteException
import fr.cklla.pellicule.data.repository.FakeAuthRepository
import fr.cklla.pellicule.data.repository.FakeEpisodeDao
import fr.cklla.pellicule.data.repository.FakeMediaDao
import fr.cklla.pellicule.data.repository.FakeOutboxDao
import fr.cklla.pellicule.data.repository.fakeSyncer
import fr.cklla.pellicule.data.repository.toEntity
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaSyncerTest {

    private val remote = FakeRemoteMediaDataSource()
    private val mediaDao = FakeMediaDao()
    private val episodeDao = FakeEpisodeDao()
    private val outbox = FakeOutboxDao()
    private val auth = FakeAuthRepository()
    private val syncer = fakeSyncer(remote, outbox, mediaDao, episodeDao, auth)

    private val fallout = Media(id = "fallout", title = "Fallout", type = MediaType.SERIE, status = WatchStatus.EN_COURS)
    private val dune = Media(id = "dune", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR)

    private suspend fun storeLocally(media: Media) = mediaDao.insert(media.toEntity())

    private suspend fun enqueue(kind: PendingOperationKind, mediaId: String, episodes: Set<EpisodeKey> = emptySet()) {
        when (kind) {
            PendingOperationKind.UPSERT_MEDIA -> outbox.enqueueUpsert(mediaId)
            PendingOperationKind.DELETE_MEDIA -> outbox.enqueueDelete(mediaId)
            else -> outbox.enqueueEpisodes(kind, mediaId, episodes)
        }
    }

    // --- flush ---

    @Test
    fun `flush rejoue un contenu avant ses episodes`() = runTest {
        storeLocally(fallout)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        enqueue(PendingOperationKind.ADD_EPISODES, "fallout", setOf(EpisodeKey(1, 1)))

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("upsert:fallout", "add:fallout"), remote.writeLog)
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `flush envoie l'etat le plus recent du contenu et non celui de l'ecriture`() = runTest {
        storeLocally(fallout)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        mediaDao.update(fallout.copy(status = WatchStatus.VU, rating = 5).toEntity())

        syncer.flush()

        assertEquals(WatchStatus.VU, remote.upsertedMedia.single().status)
        assertEquals(5, remote.upsertedMedia.single().rating)
    }

    @Test
    fun `une erreur reseau arrete l'envoi et conserve toute la file`() = runTest {
        storeLocally(fallout)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        enqueue(PendingOperationKind.ADD_EPISODES, "fallout", setOf(EpisodeKey(1, 1)))
        remote.offline = true

        assertEquals(FlushResult.RETRY_LATER, syncer.flush())

        assertEquals(2, outbox.pending.size)
        assertTrue(remote.writeLog.isEmpty())
    }

    @Test
    fun `une operation ne sort de la file qu'apres confirmation du serveur`() = runTest {
        storeLocally(fallout)
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")
        // Le serveur confirme la première écriture puis tombe.
        remote.writesBeforeFailure = 1

        assertEquals(FlushResult.RETRY_LATER, syncer.flush())

        assertEquals(listOf("fallout"), remote.media.value.map { it.id })
        assertEquals(listOf(PendingOperationKind.UPSERT_MEDIA.name), outbox.pending.map { it.kind })
        assertEquals(listOf("dune"), outbox.pending.map { it.mediaId })
    }

    @Test
    fun `l'envoi reprend a la premiere operation restante apres une erreur`() = runTest {
        storeLocally(fallout)
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")
        remote.writeFailure = java.io.IOException("serveur 503")
        assertEquals(FlushResult.RETRY_LATER, syncer.flush())
        remote.writeFailure = null

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(setOf("fallout", "dune"), remote.media.value.map { it.id }.toSet())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `une file entierement refusee definitivement se vide sans boucle infinie`() = runTest {
        storeLocally(fallout)
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")
        remote.writeFailure = PermanentRemoteException("contrainte violée")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertTrue(outbox.pending.isEmpty())
        assertTrue(remote.media.value.isEmpty())
    }

    @Test
    fun `un refus definitif d'une operation laisse partir la suivante`() = runTest {
        // Épisodes d'un contenu absent du serveur : clé étrangère violée (refus définitif), puis un
        // contenu valide derrière.
        storeLocally(dune)
        enqueue(PendingOperationKind.ADD_EPISODES, "inconnu", setOf(EpisodeKey(1, 1)))
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("dune"), remote.media.value.map { it.id })
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `rejouer deux fois les memes operations ne cree ni doublon ni erreur`() = runTest {
        storeLocally(fallout)
        repeat(2) {
            enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
            enqueue(PendingOperationKind.ADD_EPISODES, "fallout", setOf(EpisodeKey(1, 1)))
            enqueue(PendingOperationKind.REMOVE_EPISODES, "fallout", setOf(EpisodeKey(9, 9)))
        }

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(1, remote.media.value.size)
        assertEquals(setOf(EpisodeKey(1, 1)), remote.episodes.value["fallout"])
    }

    @Test
    fun `supprimer un contenu absent du serveur ne fait pas d'erreur`() = runTest {
        enqueue(PendingOperationKind.DELETE_MEDIA, "jamais-envoye")

        assertEquals(FlushResult.DONE, syncer.flush())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `un contenu supprime localement depuis n'envoie rien et sort de la file`() = runTest {
        enqueue(PendingOperationKind.UPSERT_MEDIA, "disparu")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertTrue(remote.upsertedMedia.isEmpty())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `une operation de nature inconnue est abandonnee sans bloquer la file`() = runTest {
        storeLocally(dune)
        outbox.insert(PendingOperationEntity(kind = "NATURE_FUTURE", mediaId = "dune"))
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("dune"), remote.media.value.map { it.id })
    }

    @Test
    fun `sans compte connecte rien n'est envoye et rien n'est abandonne`() = runTest {
        val signedOut = FakeAuthRepository(user = null)
        val syncerSignedOut = fakeSyncer(remote, outbox, mediaDao, episodeDao, signedOut)
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        assertEquals(FlushResult.RETRY_LATER, syncerSignedOut.flush())

        assertTrue(remote.writeLog.isEmpty())
        assertEquals(1, outbox.pending.size)
    }

    @Test
    fun `l'envoi attend la fin du chargement de la session au lancement`() = runTest {
        val loading = FakeAuthRepository().apply { startInitializing() }
        val syncerLoading = fakeSyncer(remote, outbox, mediaDao, episodeDao, loading)
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        val result = async { syncerLoading.flush() }
        runCurrent()
        assertFalse(result.isCompleted)
        assertTrue(remote.writeLog.isEmpty())
        loading.signInAs(AuthUser(uid = "u", displayName = "Spectateur"))
        advanceUntilIdle()

        assertEquals(FlushResult.DONE, result.await())
        assertEquals(listOf("dune"), remote.media.value.map { it.id })
    }

    @Test
    fun `un envoi simultane ne rejoue pas deux fois la meme operation`() = runTest {
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        val first = async { syncer.flush() }
        val second = async { syncer.flush() }

        assertEquals(FlushResult.DONE, first.await())
        assertEquals(FlushResult.DONE, second.await())
        assertEquals(1, remote.upsertedMedia.size)
    }

    // --- miroir ---

    @Test
    fun `le miroir ne touche pas aux episodes ayant une operation en attente`() = runTest {
        storeLocally(fallout)
        // Local : (1,2) coché hors ligne, (1,1) décoché hors ligne. Serveur : (1,1) vu, (1,3) vu ailleurs.
        episodeDao.insertAll(listOf(WatchedEpisodeEntity("fallout", 1, 2)))
        enqueue(PendingOperationKind.ADD_EPISODES, "fallout", setOf(EpisodeKey(1, 2)))
        enqueue(PendingOperationKind.REMOVE_EPISODES, "fallout", setOf(EpisodeKey(1, 1)))
        remote.media.value = listOf(fallout)
        remote.episodes.value = mapOf("fallout" to setOf(EpisodeKey(1, 1), EpisodeKey(1, 3)))

        assertTrue(syncer.mirror())

        val local = episodeDao.getWatchedOnce("fallout").map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet()
        // (1,2) conservé, (1,1) pas recréé, (1,3) venu de l'autre appareil appliqué.
        assertEquals(setOf(EpisodeKey(1, 2), EpisodeKey(1, 3)), local)
    }

    @Test
    fun `le miroir ne reecrit pas un contenu ayant une modification en attente`() = runTest {
        val edited = fallout.copy(status = WatchStatus.VU)
        storeLocally(edited)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "fallout")
        remote.media.value = listOf(fallout)

        assertTrue(syncer.mirror())

        assertEquals(WatchStatus.VU, mediaDao.getByIdOnce("fallout")?.status?.let { WatchStatus.valueOf(it) })
    }

    @Test
    fun `le miroir ne supprime pas un contenu qui attend d'etre cree`() = runTest {
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        assertTrue(syncer.mirror())

        assertEquals(listOf("dune"), mediaDao.getAllIds())
    }

    @Test
    fun `un contenu supprime ailleurs part d'ici avec ses operations d'episodes en attente`() = runTest {
        storeLocally(fallout)
        enqueue(PendingOperationKind.ADD_EPISODES, "fallout", setOf(EpisodeKey(1, 1)))

        assertTrue(syncer.mirror())

        assertTrue(mediaDao.getAllIds().isEmpty())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `une lecture qui echoue ne modifie rien`() = runTest {
        storeLocally(dune)
        remote.offline = true

        val failure = runCatching { syncer.mirror() }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertEquals(listOf("dune"), mediaDao.getAllIds())
    }

    @Test
    fun `un etat lu avant un envoi reussi est ignore puis relu`() = runTest {
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")
        var armed = true
        // Le serveur est vide au moment de la lecture ; l'envoi aboutit pendant celle-ci.
        remote.onFetch = {
            if (armed) {
                armed = false
                syncer.flush()
            }
        }

        assertTrue(syncer.mirror())

        assertEquals(2, remote.fetchCount)
        assertEquals(listOf("dune"), mediaDao.getAllIds())
    }

    @Test
    fun `le miroir abandonne apres plusieurs lectures toutes rendues perimees`() = runTest {
        storeLocally(dune)
        // Chaque lecture voit une nouvelle opération partir pendant qu'elle est en cours.
        remote.onFetch = {
            enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")
            syncer.flush()
        }

        assertFalse(syncer.mirror())

        assertEquals(3, remote.fetchCount)
        assertEquals(listOf("dune"), mediaDao.getAllIds())
    }

    // --- premier lancement et déconnexion ---

    @Test
    fun `la mise en file initiale place les contenus avant leurs episodes`() = runTest {
        storeLocally(fallout)
        storeLocally(dune)
        episodeDao.insertAll(listOf(WatchedEpisodeEntity("fallout", 1, 1), WatchedEpisodeEntity("fallout", 2, 1)))

        assertTrue(syncer.enqueueLocalIfServerEmpty())
        syncer.flush()

        assertEquals(setOf("upsert:fallout", "upsert:dune", "add:fallout"), remote.writeLog.toSet())
        assertTrue(remote.writeLog.indexOf("add:fallout") > remote.writeLog.lastIndexOf("upsert:dune"))
        assertEquals(setOf(EpisodeKey(1, 1), EpisodeKey(2, 1)), remote.episodes.value["fallout"])
    }

    @Test
    fun `la mise en file initiale ne fait rien si le serveur a deja du contenu`() = runTest {
        storeLocally(dune)
        remote.media.value = listOf(fallout)

        assertFalse(syncer.enqueueLocalIfServerEmpty())

        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `la mise en file initiale ne fait rien sans suivi local`() = runTest {
        assertFalse(syncer.enqueueLocalIfServerEmpty())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `la mise en file initiale sur une lecture ratee leve l'erreur au lieu de supposer un serveur vide`() = runTest {
        storeLocally(dune)
        remote.offline = true

        val failure = runCatching { syncer.enqueueLocalIfServerEmpty() }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `clearLocalData vide le suivi et la file`() = runTest {
        storeLocally(dune)
        enqueue(PendingOperationKind.UPSERT_MEDIA, "dune")

        syncer.clearLocalData()

        assertTrue(mediaDao.getAllIds().isEmpty())
        assertTrue(outbox.pending.isEmpty())
    }
}
