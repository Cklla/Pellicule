package fr.cklla.pellicule.domain.repository

import fr.cklla.pellicule.domain.model.EpisodeInfo
import fr.cklla.pellicule.domain.model.Resource
import fr.cklla.pellicule.domain.model.Season

/**
 * Métadonnées TMDB d'une série/anime déjà suivi (liste des saisons, épisodes d'une saison) — pas
 * le statut vu/non-vu, propre au suivi local (voir [EpisodeRepository]).
 */
interface TvDetailsRepository {

    /** Saisons d'une série/anime, dans l'ordre renvoyé par TMDB. */
    suspend fun getSeasons(tmdbId: Long): Resource<List<Season>>

    /** Épisodes d'une saison donnée. */
    suspend fun getEpisodes(tmdbId: Long, seasonNumber: Int): Resource<List<EpisodeInfo>>
}
