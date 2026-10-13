package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * Accès au suivi stocké sur le serveur, sur le modèle métier [Media] plutôt que sur des types du
 * SDK. Interface séparée de son implémentation pour pouvoir la remplacer par un fake en test
 * unitaire. Toutes les opérations portent sur le compte connecté : c'est la session qui désigne
 * l'utilisateur, et le serveur n'expose que ses lignes.
 *
 * Les écritures sont idempotentes (voir chaque méthode) : la file d'envoi les rejoue après un succès
 * non confirmé sans rien casser.
 */
interface RemoteMediaDataSource {

    /**
     * Signal « quelque chose a peut-être changé sur le serveur » : émis à chaque (re)connexion de
     * l'écoute temps réel, puis à chaque changement. Il ne transporte aucune donnée, l'appelant refait
     * un [fetchAll] complet. Se termine par une exception si l'écoute échoue.
     */
    fun changes(): Flow<Unit>

    /**
     * État complet du suivi. Lève une exception si la lecture échoue ou n'est pas complète : un
     * résultat partiel serait pris pour un serveur qui a supprimé le reste.
     */
    suspend fun fetchAll(): RemoteSnapshot

    /** Crée ou remplace le contenu (même identifiant, mêmes champs). */
    suspend fun upsertMedia(media: Media)

    /** Supprime le contenu et ses épisodes vus ; ne fait rien s'il n'existe pas. */
    suspend fun deleteMedia(mediaId: String)

    /** Marque [episodes] comme vus ; un épisode déjà vu est ignoré. */
    suspend fun addWatchedEpisodes(mediaId: String, episodes: Set<EpisodeKey>)

    /** Retire [episodes] des épisodes vus ; un épisode déjà absent est ignoré. */
    suspend fun removeWatchedEpisodes(mediaId: String, episodes: Set<EpisodeKey>)
}

/** Contenu du serveur : les contenus suivis, et les épisodes vus par identifiant de contenu. */
data class RemoteSnapshot(
    val media: List<Media>,
    val watchedEpisodes: Map<String, Set<EpisodeKey>>,
)

/**
 * Écriture refusée définitivement par le serveur (contrainte violée, accès refusé) : la rejouer
 * donnerait la même réponse. À distinguer d'une panne réseau ou serveur, qui se réessaie.
 */
class PermanentRemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)
