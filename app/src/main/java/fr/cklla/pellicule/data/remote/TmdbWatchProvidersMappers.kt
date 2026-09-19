package fr.cklla.pellicule.data.remote

import fr.cklla.pellicule.data.remote.dto.TmdbWatchProviderDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersCountryDto
import fr.cklla.pellicule.data.remote.dto.TmdbWatchProvidersResponseDto
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.model.WatchProvider

/** Code pays des offres retenues : seules les plateformes disponibles en France sont affichées. */
const val FRANCE_COUNTRY_CODE = "FR"

/**
 * Déclinaisons d'une même plateforme chez TMDB : offre avec publicité ("Netflix Standard with
 * Ads"), ou revente via la boutique d'un tiers ("HBO Max Amazon Channel"). Elles pointent vers le
 * même service pour le spectateur, donc une seule entrée est affichée.
 */
private val VARIANT_SUFFIX = Regex(
    """\s+(?:standard\s+|basic\s+|premium\s+)?with\s+ads$""" +
        """|\s+(?:amazon|apple\s*tv\+?|roku|canal\+|orange|free|molotov)\s+channel$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Orthographes concurrentes d'un même service chez TMDB, ramenées à une seule entrée (clé en
 * minuscules, valeur telle qu'on veut l'afficher).
 */
private val NAME_ALIASES = mapOf(
    "anime digital network" to "Animation Digital Network",
)

/**
 * Une réponse sans aucun pays signifie qu'aucune donnée n'existe pour ce contenu
 * ([WatchAvailability.Unknown]) ; une réponse qui couvre d'autres pays mais pas la France signifie
 * au contraire qu'il n'y a réellement pas d'offre française ([WatchAvailability.Known] aux listes
 * vides).
 */
fun TmdbWatchProvidersResponseDto.toDomain(): WatchAvailability {
    val countries = results.orEmpty()
    if (countries.isEmpty()) return WatchAvailability.Unknown
    val france = countries[FRANCE_COUNTRY_CODE] ?: return WatchAvailability.Known(emptyList(), emptyList(), null)
    return france.toDomain()
}

private fun TmdbWatchProvidersCountryDto.toDomain(): WatchAvailability.Known = WatchAvailability.Known(
    // Abonnement, gratuit et publicité dans le même panier : ce sont les offres qu'on regarde
    // sans payer à l'acte, seule distinction qui parle à l'utilisateur.
    streaming = (flatrate.orEmpty() + free.orEmpty() + ads.orEmpty()).toProviders(),
    rentOrBuy = (rent.orEmpty() + buy.orEmpty()).toProviders(),
    link = link?.takeIf { it.isNotBlank() },
)

private fun List<TmdbWatchProviderDto>.toProviders(): List<WatchProvider> = this
    .sortedBy { it.displayPriority ?: Int.MAX_VALUE }
    .groupBy { it.canonicalName().lowercase() }
    // Chaque groupe est déjà trié : la première entrée est l'offre la plus mise en avant par TMDB,
    // c'est donc son logo qui représente la plateforme.
    .map { (_, variants) ->
        val main = variants.first()
        WatchProvider(
            id = main.providerId,
            name = main.canonicalName(),
            logoUrl = main.logoPath?.let { TmdbApi.PROVIDER_LOGO_BASE_URL + it },
        )
    }

private fun TmdbWatchProviderDto.canonicalName(): String {
    val withoutVariant = providerName.trim().replace(VARIANT_SUFFIX, "").trim()
    return NAME_ALIASES[withoutVariant.lowercase()] ?: withoutVariant
}
