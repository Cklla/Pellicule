package fr.cklla.pellicule.domain.model

/**
 * État de la connexion. Trois valeurs et non deux : au lancement, la session enregistrée n'est pas
 * encore chargée, et cet instant ne doit être pris ni pour une déconnexion (écran de connexion
 * affiché à tort) ni pour une session absente (données locales purgées).
 */
sealed interface AuthState {

    /** Session en cours de chargement : ni écran de connexion, ni application. */
    data object Initializing : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val user: AuthUser) : AuthState
}
