package fr.cklla.pellicule.ui.jellyfin

import fr.cklla.pellicule.domain.model.JellyfinPushHistoryResult

data class JellyfinSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Adresse saisie en `http://` : le mot de passe puis le jeton d'accès transiteront en clair
     * sur le réseau. Toujours autorisé (cas courant d'un serveur local sans TLS), mais signalé.
     */
    val isCleartextServerUrl: Boolean = false,
    /** `null` tant qu'aucun serveur n'est connecté ; sinon nom d'utilisateur affiché comme statut. */
    val connectedUsername: String? = null,
    val connectedServerUrl: String? = null,
    val isPushingHistory: Boolean = false,
    /** Résultat de la dernière réinjection d'historique, affiché tant qu'aucune nouvelle action n'a été lancée. */
    val pushHistoryResult: JellyfinPushHistoryResult? = null,
)
