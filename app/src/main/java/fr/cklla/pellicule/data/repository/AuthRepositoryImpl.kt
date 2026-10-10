package fr.cklla.pellicule.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.di.ApplicationScope
import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Implémentation Supabase de [AuthRepository] : Credential Manager pour le sélecteur de compte
 * Google, puis échange du jeton Google contre une session Supabase.
 */
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    supabase: SupabaseClient,
    private val sessionManager: SessionManager,
    @ApplicationScope repositoryScope: CoroutineScope,
) : AuthRepository {

    private val auth = supabase.auth

    // Au lancement le SDK charge la session enregistrée : tant qu'elle ne l'est pas, l'état reste
    // `Initializing` (voir `toAuthState` pour le cas du jeton non renouvelable hors ligne).
    override val authState: StateFlow<AuthState> = auth.sessionStatus
        .map { status -> status.toAuthState(storedUser = sessionManager.loadSessionOrNull()?.user) }
        .stateIn(repositoryScope, SharingStarted.Eagerly, AuthState.Initializing)

    override val currentUser: StateFlow<AuthUser?> = authState
        .map { (it as? AuthState.SignedIn)?.user }
        .stateIn(repositoryScope, SharingStarted.Eagerly, null)

    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val signedOut: SharedFlow<Unit> = _signedOut

    override suspend fun signIn(context: Context): Resource<AuthUser> = runCatching {
        val nonce = GoogleNonce.generate()
        val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce.hashed)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        val credential = CredentialManager.create(context).getCredential(context, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Type d'identifiant inattendu : ${credential.type}"
        }
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWith(IDToken) {
            idToken = googleIdToken
            provider = Google
            this.nonce = nonce.raw
        }
        checkNotNull(auth.currentUserOrNull()) { "Supabase n'a renvoyé aucun utilisateur après connexion." }.toAuthUser()
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { e ->
            if (e is CancellationException) throw e
            val message = if (e is GetCredentialCancellationException) {
                "Connexion annulée."
            } else {
                SignInFailure.log(e)
                "Impossible de se connecter avec Google.\n(${SignInFailure.describe(e)})"
            }
            Resource.Error(message, e)
        },
    )

    override suspend fun signOut() {
        // Hors ligne, la révocation côté serveur échoue : la session locale doit pourtant être
        // fermée, sans quoi l'utilisateur ne pourrait pas se déconnecter sans réseau.
        try {
            auth.signOut()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            auth.clearSession()
        }
        _signedOut.emit(Unit)
        // Le système garde de son côté la trace du compte Google associé à l'app : sans ce
        // nettoyage, la reconnexion suivante peut resélectionner le compte précédent sans jamais
        // repasser par le sélecteur. Échec sans conséquence (la session Supabase, elle, est déjà
        // fermée), d'où le runCatching.
        runCatching {
            CredentialManager.create(applicationContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
