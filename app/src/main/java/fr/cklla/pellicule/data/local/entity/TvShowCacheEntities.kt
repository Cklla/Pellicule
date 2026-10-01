package fr.cklla.pellicule.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Métadonnées TMDB d'une série/anime, remplies par un seul `GET /tv/{id}`. Cache purement local à
 * l'appareil (jamais synchronisé vers Firestore) et indexé par `tmdbId` plutôt que par l'id d'un
 * contenu suivi : pas de clé étrangère vers `media`, donc le miroir Firestore → Room ne peut pas
 * l'effacer en cascade.
 *
 * Les champs `last*`/`next*` décrivent le dernier épisode diffusé et le prochain annoncé ; ils
 * servent aussi de socle à un futur calendrier des sorties.
 */
@Entity(tableName = "tv_show_cache")
data class TvShowCacheEntity(
    @PrimaryKey val tmdbId: Long,
    val tmdbStatus: String?,
    val lastSeason: Int?,
    val lastEpisode: Int?,
    val lastAirDate: String?,
    val lastTitle: String?,
    val nextSeason: Int?,
    val nextEpisode: Int?,
    val nextAirDate: String?,
    val nextTitle: String?,
    val fetchedAt: Long,
)

/**
 * Nombre d'épisodes d'une saison (même appel que [TvShowCacheEntity]). [episodesFetchedAt] vaut
 * `null` tant que le détail des épisodes de la saison (`tv_episode_cache`) n'a pas été chargé.
 */
@Entity(tableName = "tv_season_cache", primaryKeys = ["tmdbId", "seasonNumber"])
data class TvSeasonCacheEntity(
    val tmdbId: Long,
    val seasonNumber: Int,
    val episodeCount: Int,
    val episodesFetchedAt: Long?,
)

/** Titre et date de diffusion d'un épisode, chargés saison par saison à la demande. */
@Entity(tableName = "tv_episode_cache", primaryKeys = ["tmdbId", "seasonNumber", "episodeNumber"])
data class TvEpisodeCacheEntity(
    val tmdbId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val airDate: String?,
)
