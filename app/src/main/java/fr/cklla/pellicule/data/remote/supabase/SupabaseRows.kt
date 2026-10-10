package fr.cklla.pellicule.data.remote.supabase

import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lignes des tables `media` et `watched_episode`, telles que l'API REST les échange. Aucune valeur par
 * défaut sur les champs : le sérialiseur n'écrit pas une propriété égale à sa valeur par défaut, et un
 * `rating` repassé à `null` doit bien être envoyé comme `null` pour effacer la note côté serveur.
 * `user_id` est absent : le serveur le remplit avec le compte connecté.
 */
@Serializable
internal data class MediaRow(
    val id: String,
    val title: String,
    val type: String,
    val status: String,
    @SerialName("tmdb_id") val tmdbId: Long?,
    @SerialName("release_year") val releaseYear: Int?,
    @SerialName("poster_url") val posterUrl: String?,
    @SerialName("jellyfin_id") val jellyfinId: String?,
    val rating: Int?,
    @SerialName("watched_at") val watchedAt: Long?,
)

@Serializable
internal data class WatchedEpisodeRow(
    @SerialName("media_id") val mediaId: String,
    val season: Int,
    val episode: Int,
)

internal fun Media.toRow() = MediaRow(
    id = id,
    title = title,
    type = type.name,
    status = status.name,
    tmdbId = tmdbId,
    releaseYear = releaseYear,
    posterUrl = posterUrl,
    jellyfinId = jellyfinId,
    rating = rating,
    watchedAt = watchedAt,
)

/**
 * `null` pour une ligne au type ou au statut inconnu (ligne écrite par une version plus récente, ou
 * corrompue) : elle est ignorée plutôt que de fabriquer un [Media] à moitié valide.
 */
internal fun MediaRow.toMediaOrNull(): Media? {
    val mediaType = runCatching { MediaType.valueOf(type) }.getOrNull() ?: return null
    val watchStatus = runCatching { WatchStatus.valueOf(status) }.getOrNull() ?: return null
    return Media(
        id = id,
        title = title,
        type = mediaType,
        status = watchStatus,
        tmdbId = tmdbId,
        releaseYear = releaseYear,
        // Cette URL finit directement dans un chargeur d'images : on n'accepte que du HTTPS, plutôt
        // que de charger n'importe quel schéma présent en base.
        posterUrl = posterUrl?.takeIf { it.startsWith("https://") },
        jellyfinId = jellyfinId,
        rating = rating,
        watchedAt = watchedAt,
    )
}

internal fun EpisodeKey.toRow(mediaId: String) = WatchedEpisodeRow(mediaId, seasonNumber, episodeNumber)

internal fun WatchedEpisodeRow.toEpisodeKey() = EpisodeKey(season, episode)

/**
 * Lit toutes les pages d'une requête. L'API plafonne le nombre de lignes renvoyées par requête
 * (1000 par défaut) : une lecture unique tronquerait silencieusement un suivi un peu fourni, et la
 * synchro prendrait les lignes manquantes pour des suppressions. Une page incomplète marque la fin ;
 * une exception à n'importe quelle page fait échouer toute la lecture.
 */
internal suspend fun <T> fetchAllPages(
    pageSize: Int,
    fetchPage: suspend (from: Long, to: Long) -> List<T>,
): List<T> {
    val all = mutableListOf<T>()
    var from = 0L
    while (true) {
        val page = fetchPage(from, from + pageSize - 1)
        all += page
        if (page.size < pageSize) return all
        from += pageSize
    }
}

/**
 * Statuts HTTP qui signalent une écriture rejetée sur le fond : requête invalide ou contrainte
 * violée (400), accès refusé par le RLS (403), conflit ou clé étrangère (409), entité non traitable
 * (422). Tout le reste (401 le temps que la session se renouvelle, 404, 408, 429, 5xx) se réessaie :
 * abandonner à tort une écriture est une perte de données, la réessayer à tort est seulement lent.
 */
internal fun isPermanentFailureStatus(status: Int): Boolean = status in PERMANENT_STATUSES

private val PERMANENT_STATUSES = setOf(400, 403, 409, 422)
