package fr.cklla.pellicule.domain.repository

import android.content.Context
import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Resource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Point d'accès unique à l'authentification pour les ViewModels.
 *
 * `Context` apparaît exceptionnellement dans cette interface de domaine (partout ailleurs dans le
 * projet, le domaine ignore tout détail Android) : Credential Manager a besoin d'un contexte
 * d'Activity pour afficher sa feuille de sélection de compte Google, il n'y a pas moyen de s'en
 * passer pour cette opération précise.
 */
interface AuthRepository {

    /** État de connexion, dont l'instant où la session enregistrée n'est pas encore chargée. */
    val authState: StateFlow<AuthState>

    /**
     * Utilisateur actuellement connecté, ou `null` si personne ne l'est (ou pas encore, pendant le
     * chargement de la session : voir [authState] pour les distinguer).
     */
    val currentUser: StateFlow<AuthUser?>

    /**
     * Émet une fois par déconnexion demandée par l'utilisateur, une fois la session fermée. Seul
     * signal à partir duquel les données locales peuvent être purgées : `currentUser` vaut aussi
     * `null` avant le chargement de la session au lancement, ce qui ne doit rien effacer.
     */
    val signedOut: SharedFlow<Unit>

    /** Déclenche le flow "Se connecter avec Google" (Credential Manager). */
    suspend fun signIn(context: Context): Resource<AuthUser>

    /**
     * Déconnecte l'utilisateur courant et efface l'état de connexion mémorisé par le système.
     * `suspend` pour cette seconde partie : Credential Manager n'expose qu'une API asynchrone.
     */
    suspend fun signOut()
}
