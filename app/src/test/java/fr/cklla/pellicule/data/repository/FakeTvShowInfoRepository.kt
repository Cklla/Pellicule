package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.repository.TvShowInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Faux cache de séries en mémoire, pour tester les ViewModels et le cas d'usage sans Room ni TMDB. */
class FakeTvShowInfoRepository(initial: List<TvShowInfo> = emptyList()) : TvShowInfoRepository {

    private val shows = MutableStateFlow(initial.associateBy { it.tmdbId })

    val refreshedShows = mutableListOf<Long>()
    val refreshedSeasons = mutableListOf<Pair<Long, Int>>()

    fun put(show: TvShowInfo) {
        shows.value = shows.value + (show.tmdbId to show)
    }

    override fun observeShows(): Flow<Map<Long, TvShowInfo>> = shows

    override suspend fun getCachedShow(tmdbId: Long): TvShowInfo? = shows.value[tmdbId]

    override suspend fun refreshIfStale(tmdbId: Long) {
        refreshedShows.add(tmdbId)
    }

    override suspend fun refreshEpisodesIfStale(tmdbId: Long, seasonNumber: Int) {
        refreshedSeasons.add(tmdbId to seasonNumber)
    }
}
