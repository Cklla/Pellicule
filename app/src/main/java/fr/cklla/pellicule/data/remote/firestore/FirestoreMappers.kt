package fr.cklla.pellicule.data.remote.firestore

import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus

/**
 * Conversions entre le modèle métier [Media] et la représentation Firestore d'un document.
 *
 * Travaillent sur des `Map<String, Any?>` plutôt que sur `DocumentSnapshot` (classe du SDK
 * Firestore, impossible à instancier dans un test unitaire sans Robolectric/mock) : c'est
 * justement ce que `DocumentSnapshot.data` renvoie, donc testable sans dépendance Firebase.
 *
 * `id` est volontairement absent de la map : c'est l'id du document Firestore lui-même
 * (`.document(media.id)`), pas la peine de le dupliquer en champ. La liste de champs doit rester
 * synchronisée avec `isValidMedia` dans `firestore.rules`.
 */

private const val FIELD_TITLE = "title"
private const val FIELD_TYPE = "type"
private const val FIELD_STATUS = "status"
private const val FIELD_TMDB_ID = "tmdbId"
private const val FIELD_RELEASE_YEAR = "releaseYear"
private const val FIELD_POSTER_URL = "posterUrl"
private const val FIELD_JELLYFIN_ID = "jellyfinId"
private const val FIELD_RATING = "rating"

fun Media.toFirestoreMap(): Map<String, Any?> = mapOf(
    FIELD_TITLE to title,
    FIELD_TYPE to type.name,
    FIELD_STATUS to status.name,
    FIELD_TMDB_ID to tmdbId,
    FIELD_RELEASE_YEAR to releaseYear,
    FIELD_POSTER_URL to posterUrl,
    FIELD_JELLYFIN_ID to jellyfinId,
    FIELD_RATING to rating,
)

// Firestore n'a pas de type `Int` natif (tout nombre entier remonte en `Long`) : cast via `Number`
// plutôt que `Long`/`Int` strict, pour rester correct aussi bien face à un vrai document Firestore
// qu'à une map construite à la main en Kotlin (tests). Un type/statut absent ou inconnu (document
// corrompu, champ renommé côté futur) fait échouer tout le mapping plutôt que de fabriquer un
// `Media` à moitié valide.
fun mapToMedia(id: String, data: Map<String, Any?>): Media? {
    val type = (data[FIELD_TYPE] as? String)?.let { raw ->
        runCatching { MediaType.valueOf(raw) }.getOrNull()
    } ?: return null
    val status = (data[FIELD_STATUS] as? String)?.let { raw ->
        runCatching { WatchStatus.valueOf(raw) }.getOrNull()
    } ?: return null
    val title = data[FIELD_TITLE] as? String ?: return null

    return Media(
        id = id,
        title = title,
        type = type,
        status = status,
        tmdbId = (data[FIELD_TMDB_ID] as? Number)?.toLong(),
        releaseYear = (data[FIELD_RELEASE_YEAR] as? Number)?.toInt(),
        // Cette URL finit directement dans un chargeur d'images : on n'accepte que du HTTPS,
        // plutôt que de charger n'importe quel schéma présent dans le document.
        posterUrl = (data[FIELD_POSTER_URL] as? String)?.takeIf { it.startsWith("https://") },
        jellyfinId = data[FIELD_JELLYFIN_ID] as? String,
        rating = (data[FIELD_RATING] as? Number)?.toInt(),
    )
}
