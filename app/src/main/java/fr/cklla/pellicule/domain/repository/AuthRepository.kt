package fr.cklla.pellicule.domain.repository

import android.content.Context
import fr.cklla.pellicule.domain.model.AuthUser
import fr.cklla.pellicule.domain.model.Resource
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

    /** Utilisateur actuellement connecté, ou `null` si personne n'est connecté. */
    val currentUser: StateFlow<AuthUser?>

    /** Déclenche le flow "Se connecter avec Google" (Credential Manager). */
    suspend fun signIn(context: Context): Resource<AuthUser>

    /**
     * Déconnecte l'utilisateur courant et efface l'état de connexion mémorisé par le système.
     * `suspend` pour cette seconde partie : Credential Manager n'expose qu'une API asynchrone.
     */
    suspend fun signOut()
}
