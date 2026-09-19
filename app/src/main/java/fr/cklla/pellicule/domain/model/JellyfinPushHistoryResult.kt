package fr.cklla.pellicule.domain.model

/** Compte-rendu d'une réinjection manuelle de l'historique de vus vers Jellyfin (voir `JellyfinRepository.pushWatchedHistory`). */
data class JellyfinPushHistoryResult(
    val moviesMarkedPlayed: Int,
    val episodesMarkedPlayed: Int,
)
