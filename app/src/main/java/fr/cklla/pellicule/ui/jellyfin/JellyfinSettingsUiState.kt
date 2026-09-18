package fr.cklla.pellicule.ui.jellyfin

data class JellyfinSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    /** `null` tant qu'aucun serveur n'est connecté ; sinon nom d'utilisateur affiché comme statut. */
    val connectedUsername: String? = null,
    val connectedServerUrl: String? = null,
)
