package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.local.OutboxDao
import fr.cklla.pellicule.data.local.TransactionRunner
import fr.cklla.pellicule.data.remote.FakeRemoteMediaDataSource
import fr.cklla.pellicule.data.sync.InitialUploadStore
import fr.cklla.pellicule.data.sync.MediaSyncer
import fr.cklla.pellicule.data.sync.OutboxScheduler
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Transaction sans effet : les DAO en mémoire n'ont ni transaction ni retour arrière. */
object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

/**
 * Remplace WorkManager : exécute l'envoi tout de suite (la portée non confinée le déroule en ligne,
 * le faux serveur ne suspendant jamais), ou le laisse en attente quand [autoFlush] est à `false` pour
 * tester ce qui se passe tant que la file n'est pas partie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestOutboxScheduler(
    private val flush: suspend () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
) : OutboxScheduler {

    var autoFlush = true
    var requestCount = 0
        private set

    override fun requestFlush() {
        requestCount++
        if (autoFlush) scope.launch { flush() }
    }
}

fun fakeSyncer(
    remote: FakeRemoteMediaDataSource = FakeRemoteMediaDataSource(enforceForeignKey = false),
    outboxDao: OutboxDao = FakeOutboxDao(),
    mediaDao: MediaDao = FakeMediaDao(),
    episodeDao: EpisodeDao = FakeEpisodeDao(),
    authRepository: AuthRepository = FakeAuthRepository(),
    transactions: TransactionRunner = ImmediateTransactionRunner,
    initialUpload: InitialUploadStore = FakeInitialUploadStore(),
) = MediaSyncer(remote, outboxDao, mediaDao, episodeDao, transactions, authRepository, initialUpload)

/**
 * Construit un [MediaRepositoryImpl] de test, avec des fakes par défaut pour le serveur/l'auth : la
 * plupart des tests de ViewModel n'ont besoin que de Room (via [dao]) et n'observent pas la synchro
 * elle-même (couverte par `MediaRepositoryImplTest` et `MediaSyncerTest`). Pour qu'un
 * [EpisodeRepositoryImpl] partage la même file et la même synchro, lui passer les mêmes [outboxDao],
 * [syncer] et [scheduler].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun fakeMediaRepository(
    dao: MediaDao,
    remote: FakeRemoteMediaDataSource = FakeRemoteMediaDataSource(enforceForeignKey = false),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
    timeSource: TimeSource = TimeSource { System.currentTimeMillis() },
    episodeDao: EpisodeDao = FakeEpisodeDao(),
    outboxDao: OutboxDao = FakeOutboxDao(),
    syncer: MediaSyncer = fakeSyncer(remote, outboxDao, dao, episodeDao, authRepository),
    scheduler: OutboxScheduler = TestOutboxScheduler({ syncer.flush() }),
): MediaRepository = MediaRepositoryImpl(
    mediaDao = dao,
    outboxDao = outboxDao,
    transactions = ImmediateTransactionRunner,
    remoteDataSource = remote,
    syncer = syncer,
    outboxScheduler = scheduler,
    authRepository = authRepository,
    timeSource = timeSource,
    repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
)

/**
 * Construit un [EpisodeRepositoryImpl] de test : par défaut un Room en mémoire, un faux serveur et un
 * utilisateur connecté, avec un envoi immédiat.
 */
fun fakeEpisodeRepository(
    dao: EpisodeDao = FakeEpisodeDao(),
    remote: FakeRemoteMediaDataSource = FakeRemoteMediaDataSource(enforceForeignKey = false),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
    outboxDao: OutboxDao = FakeOutboxDao(),
    mediaDao: MediaDao = FakeMediaDao(),
    syncer: MediaSyncer = fakeSyncer(remote, outboxDao, mediaDao, dao, authRepository),
    scheduler: OutboxScheduler = TestOutboxScheduler({ syncer.flush() }),
): EpisodeRepositoryImpl = EpisodeRepositoryImpl(
    episodeDao = dao,
    outboxDao = outboxDao,
    transactions = ImmediateTransactionRunner,
    outboxScheduler = scheduler,
)

/** Mémoire en mémoire de l'envoi initial : le test peut la préremplir ou la relire. */
class FakeInitialUploadStore(var doneFor: String? = null) : InitialUploadStore {
    override fun isDone(userId: String) = doneFor == userId
    override fun markDone(userId: String) {
        doneFor = userId
    }

    override fun clear() {
        doneFor = null
    }
}
