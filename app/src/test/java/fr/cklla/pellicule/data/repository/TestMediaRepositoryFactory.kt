package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.MediaDao
import fr.cklla.pellicule.data.remote.firestore.FakeFirestoreMediaDataSource
import fr.cklla.pellicule.domain.repository.MediaRepository
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
): MediaRepository = MediaRepositoryImpl(
    mediaDao = dao,
    firestoreDataSource = firestoreDataSource,
    authRepository = authRepository,
    repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
)
