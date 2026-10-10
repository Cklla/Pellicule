package fr.cklla.pellicule.data.repository

import android.content.Context
import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Double de test en mémoire : pas de vrai Credential Manager/Firebase à solliciter ici. */
class FakeAuthRepository(
    user: AuthUser? = AuthUser(uid = "fake-uid", displayName = "Spectateur Test"),
    /** Résultat renvoyé par [signIn], configurable au cas par cas selon le test. */
    var signInResult: Resource<AuthUser> = Resource.Error("signIn() non configuré par ce fake"),
) : AuthRepository {

    private val _currentUser = MutableStateFlow(user)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    private val _authState = MutableStateFlow<AuthState>(user?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState

    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val signedOut: SharedFlow<Unit> = _signedOut

    var signOutCallCount = 0
        private set

    override suspend fun signIn(context: Context): Resource<AuthUser> {
        if (signInResult is Resource.Success) {
            signInAs((signInResult as Resource.Success).data)
        }
        return signInResult
    }

    override suspend fun signOut() {
        signOutCallCount++
        setSignedOutState()
        _signedOut.emit(Unit)
    }

    /** Simule une session absente (chargement au lancement, jeton expiré) : aucune déconnexion demandée. */
    fun dropSessionWithoutSignOut() = setSignedOutState()

    /** Simule le chargement de la session au lancement : ni connecté ni déconnecté. */
    fun startInitializing() {
        _currentUser.value = null
        _authState.value = AuthState.Initializing
    }

    private fun setSignedOutState() {
        _currentUser.value = null
        _authState.value = AuthState.SignedOut
    }

    /** Simule une connexion déjà effective, sans passer par le flow [signIn] (Credential Manager). */
    fun signInAs(user: AuthUser) {
        // L'état avant l'utilisateur, comme en production où `currentUser` en est dérivé : un
        // collecteur de `currentUser` réveillé ici doit déjà voir l'état « connecté ».
        _authState.value = AuthState.SignedIn(user)
        _currentUser.value = user
    }
}
