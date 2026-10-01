package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.entity.TvEpisodeCacheEntity
import fr.cklla.pellicule.data.local.entity.TvSeasonCacheEntity
import fr.cklla.pellicule.data.local.entity.TvShowCacheEntity
import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeToAirDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonDto
import fr.cklla.pellicule.data.remote.dto.TmdbTvDetailsDto
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.EpisodeAirInfo
import fr.cklla.pellicule.domain.model.EpisodeDetail
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus

internal fun TmdbTvDetailsDto.toShowEntity(tmdbId: Long, fetchedAt: Long) = TvShowCacheEntity(
    tmdbId = tmdbId,
    tmdbStatus = status,
    lastSeason = lastEpisodeToAir?.seasonNumber,
    lastEpisode = lastEpisodeToAir?.episodeNumber,
    lastAirDate = lastEpisodeToAir?.airDate?.takeIf { AirDate.parse(it) != null },
    lastTitle = lastEpisodeToAir?.name?.takeIf(String::isNotBlank),
    nextSeason = nextEpisodeToAir?.seasonNumber,
    nextEpisode = nextEpisodeToAir?.episodeNumber,
    nextAirDate = nextEpisodeToAir?.airDate?.takeIf { AirDate.parse(it) != null },
    nextTitle = nextEpisodeToAir?.name?.takeIf(String::isNotBlank),
    fetchedAt = fetchedAt,
)

internal fun TmdbTvDetailsDto.toSeasonEntities(tmdbId: Long): List<TvSeasonCacheEntity> =
    seasons.map { TvSeasonCacheEntity(tmdbId, it.seasonNumber, it.episodeCount, episodesFetchedAt = null) }

internal fun TmdbSeasonDto.toEpisodeEntities(tmdbId: Long, seasonNumber: Int): List<TvEpisodeCacheEntity> =
    episodes.map {
        TvEpisodeCacheEntity(
            tmdbId = tmdbId,
            seasonNumber = seasonNumber,
            episodeNumber = it.episodeNumber,
            title = it.name.takeIf(String::isNotBlank),
            airDate = it.airDate?.takeIf { date -> AirDate.parse(date) != null },
        )
    }

internal fun assembleShow(
    show: TvShowCacheEntity,
    seasons: List<TvSeasonCacheEntity>,
    episodes: List<TvEpisodeCacheEntity>,
) = TvShowInfo(
    tmdbId = show.tmdbId,
    status = TvShowStatus.fromTmdb(show.tmdbStatus),
    seasonEpisodeCounts = seasons.associate { it.seasonNumber to it.episodeCount },
    lastAired = airInfo(show.lastSeason, show.lastEpisode, show.lastAirDate, show.lastTitle),
    nextToAir = airInfo(show.nextSeason, show.nextEpisode, show.nextAirDate, show.nextTitle),
    episodes = episodes.associate {
        EpisodeKey(it.seasonNumber, it.episodeNumber) to EpisodeDetail(it.title, AirDate.parse(it.airDate))
    },
)

private fun airInfo(season: Int?, episode: Int?, airDate: String?, title: String?): EpisodeAirInfo? {
    if (season == null || episode == null) return null
    return EpisodeAirInfo(EpisodeKey(season, episode), AirDate.parse(airDate), title)
}
