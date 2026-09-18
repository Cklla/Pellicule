package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.remote.jellyfin.JellyfinApi
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthRequestDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinAuthResponseDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinEpisodeDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinEpisodesResponseDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinItemDto
import fr.cklla.pellicule.data.remote.jellyfin.dto.JellyfinItemsResponseDto

/** Faux client Jellyfin en mémoire, utilisé pour tester [JellyfinRepositoryImpl] sans appel réseau réel. */
class FakeJellyfinApi : JellyfinApi {

    var authResponse: JellyfinAuthResponseDto? = null
    var authError: Throwable? = null
    var items: List<JellyfinItemDto> = emptyList()

    /** Épisodes par id de série Jellyfin, extrait de l'URL `.../Shows/{seriesId}/Episodes`. */
    var episodesBySeriesId: Map<String, List<JellyfinEpisodeDto>> = emptyMap()

    val playedUrls = mutableListOf<String>()
    val unplayedUrls = mutableListOf<String>()

    override suspend fun authenticateByName(url: String, authHeader: String, body: JellyfinAuthRequestDto): JellyfinAuthResponseDto {
        authError?.let { throw it }
        return authResponse ?: error("FakeJellyfinApi.authResponse non configuré")
    }

    override suspend fun getItems(
        url: String,
        authHeader: String,
        includeItemTypes: String,
        recursive: Boolean,
        fields: String,
    ): JellyfinItemsResponseDto = JellyfinItemsResponseDto(items)

    override suspend fun getSeriesEpisodes(url: String, authHeader: String, userId: String, fields: String): JellyfinEpisodesResponseDto {
        val seriesId = url.substringAfter("/Shows/").substringBefore("/Episodes")
        return JellyfinEpisodesResponseDto(episodesBySeriesId[seriesId].orEmpty())
    }

    override suspend fun markPlayed(url: String, authHeader: String) {
        playedUrls.add(url)
    }

    override suspend fun markUnplayed(url: String, authHeader: String) {
        unplayedUrls.add(url)
    }
}
