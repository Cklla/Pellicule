package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.EpisodeDao
import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.remote.firestore.FakeFirestoreMediaDataSource
import fr.cklla.pellicule.domain.repository.EpisodeRepository
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.domain.util.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Construit un [MediaRepositoryImpl] de test, avec des fakes par défaut pour Firestore/l'auth :
 * la plupart des tests de ViewModel n'ont besoin que de Room (via [dao]) et n'observent pas la
 * synchro Firestore elle-même (couverte par `MediaRepositoryImplTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun fakeMediaRepository(
    dao: MediaDao,
    firestoreDataSource: FakeFirestoreMediaDataSource = FakeFirestoreMediaDataSource(),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
    timeSource: TimeSource = TimeSource { System.currentTimeMillis() },
    episodeDao: EpisodeDao = FakeEpisodeDao(),
): MediaRepository = MediaRepositoryImpl(
    mediaDao = dao,
    episodeDao = episodeDao,
    firestoreDataSource = firestoreDataSource,
    authRepository = authRepository,
    timeSource = timeSource,
    repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
)

/**
 * Construit un [EpisodeRepositoryImpl] de test : par défaut un Room en mémoire, un Firestore factice et
 * un utilisateur connecté, avec une portée qui exécute les envois Firestore immédiatement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun fakeEpisodeRepository(
    dao: EpisodeDao = FakeEpisodeDao(),
    firestoreDataSource: FakeFirestoreMediaDataSource = FakeFirestoreMediaDataSource(),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
): EpisodeRepositoryImpl = EpisodeRepositoryImpl(
    episodeDao = dao,
    firestoreDataSource = firestoreDataSource,
    authRepository = authRepository,
    repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
)
