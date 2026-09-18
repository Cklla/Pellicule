package fr.cklla.pellicule.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.repository.AuthRepository
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await

private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, displayName = displayName)

/**
 * Implémentation Firebase de [AuthRepository] : Credential Manager pour le sélecteur de compte
 * Google, puis échange du jeton Google contre une session Firebase Auth.
 */
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val currentUser = MutableStateFlow(firebaseAuth.currentUser?.toAuthUser())

    init {
        // FirebaseAuth garde l'utilisateur connecté en cache disque entre deux lancements de
        // l'app : ce listener resynchronise `currentUser` à chaque connexion/déconnexion, y
        // compris celles déclenchées ailleurs que par ce Repository (peu probable ici, mais évite
        // toute divergence entre l'état réel de FirebaseAuth et ce qu'expose l'app).
        firebaseAuth.addAuthStateListener { auth -> currentUser.value = auth.currentUser?.toAuthUser() }
    }

    override suspend fun signIn(context: Context): Resource<AuthUser> = runCatching {
        // Web Client ID généré automatiquement par le plugin Gradle `google-services` à partir du
        // client OAuth `client_type: 3` de `google-services.json` — jamais codé en dur.
        val serverClientId = context.getString(R.string.default_web_client_id)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(generateNonce())
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        val credential = CredentialManager.create(context).getCredential(context, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Type d'identifiant inattendu : ${credential.type}"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(firebaseCredential).await().user
        checkNotNull(user) { "FirebaseAuth n'a renvoyé aucun utilisateur après connexion." }.toAuthUser()
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { e ->
            val message = if (e is GetCredentialCancellationException) {
                "Connexion annulée."
            } else {
                "Impossible de se connecter avec Google."
            }
            Resource.Error(message, e)
        },
    )

    override suspend fun signOut() {
        firebaseAuth.signOut()
        // Firebase oublie la session, mais le système garde de son côté la trace du compte
        // Google associé à l'app : sans ce nettoyage, la reconnexion suivante peut resélectionner
        // le compte précédent sans jamais repasser par le sélecteur. Échec sans conséquence (la
        // déconnexion Firebase, elle, a déjà eu lieu), d'où le runCatching.
        runCatching {
            CredentialManager.create(applicationContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
