package fr.cklla.pellicule.data.remote.firestore

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la collection Firestore d'un utilisateur, sur le modèle métier [Media] plutôt que sur des
 * types Firestore bruts. Interface séparée de son implémentation (contrairement à `FirestoreMappers`,
 * simples fonctions) pour pouvoir la remplacer par un fake en test unitaire — `FirebaseFirestore`
 * est une classe du SDK Android, impossible à instancier dans un test JVM sans Robolectric/mock.
 *
 * Chemin Firestore : `users/{uid}/movies/{mediaId}` — chaque utilisateur ne voit que sa propre
 * collection (voir `firestore.rules`).
 */
interface FirestoreMediaDataSource {

    /**
     * Écoute temps réel de la collection de l'utilisateur [uid]. Les épisodes vus arrivent avec leur
     * contenu, dans un même snapshot : le contenu existe donc toujours en Room avant ses épisodes.
     */
    fun observeMedia(uid: String): Flow<List<RemoteMedia>>

    /** Lecture ponctuelle (pas d'écoute), utilisée pour le bootstrap au premier lancement. */
    suspend fun fetchMediaOnce(uid: String): List<Media>

    suspend fun upsertMedia(uid: String, media: Media)

    suspend fun deleteMedia(uid: String, mediaId: String)

    /**
     * Ajoute [episodes] aux épisodes vus du contenu, sans lire ni réécrire le reste de la liste :
     * deux appareils qui cochent chacun un épisode ne s'écrasent pas, et un snapshot en retard ne
     * peut pas faire perdre un épisode déjà enregistré. L'écriture est émise avant que la fonction
     * ne suspende, donc dans l'ordre des appels.
     */
    suspend fun addWatchedEpisodes(uid: String, mediaId: String, episodes: Set<EpisodeKey>)

    /** Retire [episodes] des épisodes vus du contenu (même principe que [addWatchedEpisodes]). */
    suspend fun removeWatchedEpisodes(uid: String, mediaId: String, episodes: Set<EpisodeKey>)

    /** Écriture groupée, utilisée pour l'upload initial du suivi local pré-existant. */
    suspend fun uploadAll(uid: String, media: List<Media>)

    /**
     * Efface la copie que Firestore garde sur le disque de l'appareil. Appelé à la déconnexion :
     * vider la base Room ne suffit pas, le SDK conserve de son côté son propre cache des documents
     * du compte qui vient de se déconnecter.
     */
    suspend fun clearLocalCache()
}

/**
 * Un contenu tel que Firestore le renvoie. [watchedEpisodes] vaut `null` quand le document n'a pas
 * encore le champ : Room garde alors ses épisodes au lieu de les vider.
 */
data class RemoteMedia(val media: Media, val watchedEpisodes: Set<EpisodeKey>?)
