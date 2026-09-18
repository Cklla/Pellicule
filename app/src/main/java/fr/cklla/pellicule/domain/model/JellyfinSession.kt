package fr.cklla.pellicule.domain.model

/** Session active auprès d'un serveur Jellyfin, une fois la connexion établie (voir `JellyfinRepository`). */
data class JellyfinSession(
    val serverUrl: String,
    val userId: String,
    val username: String,
    val accessToken: String,
)
