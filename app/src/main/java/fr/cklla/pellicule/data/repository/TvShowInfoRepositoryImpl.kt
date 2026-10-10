package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.BuildConfig
import fr.cklla.pellicule.data.local.TvShowCacheDao
import fr.cklla.pellicule.data.remote.TmdbApi
import fr.cklla.pellicule.di.ApplicationScope
import fr.cklla.pellicule.domain.model.AirDate
import fr.cklla.pellicule.domain.model.TvShowInfo
import fr.cklla.pellicule.domain.model.TvShowStatus
import fr.cklla.pellicule.domain.repository.AuthRepository
import fr.cklla.pellicule.domain.repository.TvShowInfoRepository
import fr.cklla.pellicule.domain.util.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Implémentation Room + TMDB du [TvShowInfoRepository]. */
class TvShowInfoRepositoryImpl @Inject constructor(
    private val dao: TvShowCacheDao,
    private val tmdbApi: TmdbApi,
    private val timeSource: TimeSource,
    authRepository: AuthRepository,
    @ApplicationScope repositoryScope: CoroutineScope,
) : TvShowInfoRepository {

    init {
        // Ce cache ne contient que des métadonnées publiques, mais il révèle quelles séries le
        // compte précédent suivait : il part avec le reste des données locales à la déconnexion
        // demandée par l'utilisateur. Un `currentUser` à `null` ne suffit pas : c'est aussi l'état
        // avant le chargement de la session.
        repositoryScope.launch {
            authRepository.signedOut.collect { runCatching { dao.clearAll() } }
        }
    }

    override fun observeShows(): Flow<Map<Long, TvShowInfo>> =
        combine(dao.observeShows(), dao.observeSeasons(), dao.observeEpisodes()) { shows, seasons, episodes ->
            val seasonsByShow = seasons.groupBy { it.tmdbId }
            val episodesByShow = episodes.groupBy { it.tmdbId }
            shows.associate { show ->
                show.tmdbId to assembleShow(
                    show,
                    seasonsByShow[show.tmdbId].orEmpty(),
                    episodesByShow[show.tmdbId].orEmpty(),
                )
            }
        }.distinctUntilChanged()

    override suspend fun getCachedShow(tmdbId: Long): TvShowInfo? {
        val show = dao.getShow(tmdbId) ?: return null
        return assembleShow(show, dao.getSeasons(tmdbId), dao.getEpisodes(tmdbId))
    }

    override suspend fun refreshIfStale(tmdbId: Long) {
        val cached = dao.getShow(tmdbId)
        if (cached != null && !isStale(cached.fetchedAt, TvShowStatus.fromTmdb(cached.tmdbStatus))) return
        fetchAndStore(tmdbId)
    }

    override suspend fun refreshOncePerDay(tmdbId: Long) {
        val cached = dao.getShow(tmdbId)
        if (cached != null) {
            val status = TvShowStatus.fromTmdb(cached.tmdbStatus)
            val upToDate = if (status.isFinished) !isStale(cached.fetchedAt, status) else isFetchedToday(cached.fetchedAt)
            if (upToDate) return
        }
        fetchAndStore(tmdbId)
    }

    private suspend fun fetchAndStore(tmdbId: Long) {
        val details = fetch { tmdbApi.getTvDetails(tvId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY) } ?: return
        dao.replaceShow(
            show = details.toShowEntity(tmdbId, fetchedAt = timeSource.nowMillis()),
            seasons = details.toSeasonEntities(tmdbId),
        )
    }

    // Même jour calendaire que maintenant ; un horodatage dans le futur ne compte pas (voir isStale).
    private fun isFetchedToday(fetchedAt: Long): Boolean {
        val now = timeSource.nowMillis()
        return fetchedAt <= now && AirDate.fromMillis(fetchedAt) == AirDate.fromMillis(now)
    }

    override suspend fun refreshEpisodesIfStale(tmdbId: Long, seasonNumber: Int) {
        val show = dao.getShow(tmdbId) ?: return
        val season = dao.getSeasons(tmdbId).firstOrNull { it.seasonNumber == seasonNumber } ?: return
        val fetchedAt = season.episodesFetchedAt
        if (fetchedAt != null && !isStale(fetchedAt, TvShowStatus.fromTmdb(show.tmdbStatus))) return
        val details = fetch {
            tmdbApi.getTvSeason(tvId = tmdbId, seasonNumber = seasonNumber, apiKey = BuildConfig.TMDB_API_KEY)
        } ?: return
        dao.replaceSeasonEpisodes(
            season = season.copy(episodesFetchedAt = timeSource.nowMillis()),
            episodes = details.toEpisodeEntities(tmdbId, seasonNumber),
        )
    }

    // Un horodatage dans le futur (horloge de l'appareil reculée) vaut périmé : mieux vaut un appel
    // de trop qu'un cache réputé frais indéfiniment.
    private fun isStale(fetchedAt: Long, status: TvShowStatus): Boolean {
        val now = timeSource.nowMillis()
        val validity = if (status.isFinished) FINISHED_VALIDITY_MILLIS else ONGOING_VALIDITY_MILLIS
        return now < fetchedAt || now - fetchedAt >= validity
    }

    // Échec réseau avalé : le cache existant (même périmé) reste servi, et l'appelant retentera à
    // sa prochaine occasion plutôt que d'afficher une erreur pour une donnée de confort.
    private suspend fun <T> fetch(call: suspend () -> T): T? = try {
        call()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ONGOING_VALIDITY_MILLIS = 24L * 60 * 60 * 1000
        const val FINISHED_VALIDITY_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
