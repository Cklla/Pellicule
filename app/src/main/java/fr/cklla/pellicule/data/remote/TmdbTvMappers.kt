package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbEpisodeDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonDto
import fr.cklla.pellicule.data.remote.dto.TmdbSeasonSummaryDto
import fr.cklla.pellicule.data.remote.dto.TmdbTvDetailsDto
import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.Season

fun TmdbTvDetailsDto.toDomain(): List<Season> = seasons.map { it.toDomain() }

fun TmdbSeasonSummaryDto.toDomain(): Season = Season(
    seasonNumber = seasonNumber,
    name = name,
    episodeCount = episodeCount,
    posterUrl = posterPath?.let { TmdbApi.POSTER_BASE_URL + it },
)

fun TmdbSeasonDto.toDomain(): List<EpisodeInfo> = episodes.map { it.toDomain() }

fun TmdbEpisodeDto.toDomain(): EpisodeInfo = EpisodeInfo(
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = name,
    stillUrl = stillPath?.let { TmdbApi.STILL_BASE_URL + it },
)
