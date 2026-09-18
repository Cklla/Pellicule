package fr.cklla.pellicule.domain.model

/** Une saison TMDB d'une série/anime suivi (la saison 0 désigne les "spéciaux" chez TMDB). */
data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String?,
)
