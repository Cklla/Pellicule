package fr.cklla.pellicule.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.Season
import fr.cklla.pellicule.domain.model.WatchAvailability
import fr.cklla.pellicule.domain.model.WatchProvider
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.components.MediaCoverPlaceholder
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.ErrorCoral
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.SuccessGreen
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextTertiary
import fr.cklla.pellicule.ui.theme.labelRes
import fr.cklla.pellicule.ui.theme.palette
import java.util.Locale

@Composable
fun DetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Le contenu devient `null` après chargement soit parce que l'id est invalide, soit après
    // "Retirer du suivi" (voir DetailViewModel) : dans les deux cas on revient simplement à
    // l'écran précédent plutôt que d'afficher une fiche cassée.
    LaunchedEffect(uiState.isLoading, uiState.media) {
        if (!uiState.isLoading && uiState.media == null) onBackClick()
    }

    val media = uiState.media ?: return

    DetailContent(
        media = media,
        isInBacklog = uiState.isInBacklog,
        synopsis = uiState.synopsis,
        watchAvailability = uiState.watchAvailability,
        seasons = uiState.seasons,
        selectedSeasonNumber = uiState.selectedSeasonNumber,
        episodes = uiState.episodes,
        episodesLoading = uiState.episodesLoading,
        episodesErrorMessage = uiState.episodesErrorMessage,
        onBackClick = onBackClick,
        onStatusSelected = viewModel::onStatusSelected,
        onRatingSelected = viewModel::onRatingSelected,
        onSeasonSelected = viewModel::onSeasonSelected,
        onEpisodeWatchedToggled = viewModel::onEpisodeWatchedToggled,
        onRemoveMedia = viewModel::onRemoveMedia,
        onAddMedia = viewModel::onAddMedia,
        modifier = modifier,
    )
}

@Composable
private fun DetailContent(
    media: Media,
    isInBacklog: Boolean,
    synopsis: String?,
    watchAvailability: WatchAvailability?,
    seasons: List<Season>,
    selectedSeasonNumber: Int?,
    episodes: List<EpisodeUiModel>,
    episodesLoading: Boolean,
    episodesErrorMessage: String?,
    onBackClick: () -> Unit,
    onStatusSelected: (WatchStatus) -> Unit,
    onRatingSelected: (Int?) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeWatchedToggled: (EpisodeUiModel) -> Unit,
    onRemoveMedia: () -> Unit,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    // Repasser un contenu "Vu" à un autre statut démarque aussi ce qu'il efface sur Jellyfin
    // (épisodes ou film, voir `DetailViewModel.onStatusSelected`) : effet visible sur le serveur
    // réel et les autres clients (Moonfin), donc confirmation avant d'agir, comme pour le retrait.
    var pendingStatusReset by remember { mutableStateOf<WatchStatus?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        BackHeader(onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            MediaCoverPlaceholder(
                title = media.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                posterUrl = media.posterUrl,
                letterStyle = PelliculeTextStyles.coverLetterLarge,
                letterAlpha = 0.14f,
            )
            TitleSection(media = media)
            // Visible dès l'aperçu (pas besoin d'être suivi pour lire de quoi ça parle), masqué
            // tant que le synopsis n'est pas encore arrivé de TMDB plutôt que d'afficher un vide.
            if (!synopsis.isNullOrBlank()) {
                SynopsisSection(synopsis = synopsis)
            }
            // Masquée tant que la réponse TMDB n'est pas arrivée, pour ne pas annoncer une
            // disponibilité inconnue le temps de l'appel réseau.
            watchAvailability?.let { availability ->
                AvailabilitySection(availability = availability, type = media.type)
            }
            // Statut, retrait et épisodes n'ont de sens que pour un contenu réellement suivi :
            // masqués tant que la fiche n'est qu'un aperçu ouvert depuis la Recherche (voir
            // `DetailUiState.isInBacklog`).
            if (isInBacklog) {
                StatusSection(
                    selected = media.status,
                    onStatusSelected = { status ->
                        if (media.status == WatchStatus.VU && status != WatchStatus.VU) {
                            pendingStatusReset = status
                        } else {
                            onStatusSelected(status)
                        }
                    },
                )
                RatingSection(rating = media.rating, onRatingSelected = onRatingSelected)
                if (media.type != MediaType.FILM) {
                    EpisodesSection(
                        seasons = seasons,
                        selectedSeasonNumber = selectedSeasonNumber,
                        episodes = episodes,
                        isLoading = episodesLoading,
                        errorMessage = episodesErrorMessage,
                        onSeasonSelected = onSeasonSelected,
                        onEpisodeWatchedToggled = onEpisodeWatchedToggled,
                    )
                }
                RemoveLink(onClick = { showRemoveConfirm = true })
            } else {
                AddButton(onClick = onAddMedia)
            }
        }
    }

    if (showRemoveConfirm) {
        RemoveConfirmDialog(
            onConfirm = {
                showRemoveConfirm = false
                onRemoveMedia()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }

    pendingStatusReset?.let { targetStatus ->
        StatusResetConfirmDialog(
            onConfirm = {
                pendingStatusReset = null
                onStatusSelected(targetStatus)
            },
            onDismiss = { pendingStatusReset = null },
        )
    }
}

@Composable
private fun BackHeader(onBackClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = TextPrimary,
            )
            Text(
                text = stringResource(R.string.detail_back),
                style = PelliculeTextStyles.backLabel,
                color = TextTertiary,
            )
        }
        HorizontalDivider(color = BorderHairline.copy(alpha = 0.6f), thickness = 0.5.dp)
    }
}

@Composable
private fun TitleSection(media: Media) {
    Column {
        Text(text = media.title, style = PelliculeTextStyles.detailTitle, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.media_card_type_year,
                stringResource(media.type.labelRes()),
                media.releaseYear?.toString() ?: stringResource(R.string.media_card_year_unknown),
            ),
            style = PelliculeTextStyles.detailSubtitle,
            color = TextTertiary,
        )
    }
}

@Composable
private fun SynopsisSection(synopsis: String) {
    Column {
        SectionLabel(stringResource(R.string.detail_synopsis_label))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = synopsis,
            style = PelliculeTextStyles.detailSubtitle,
            color = TextTertiary,
            textAlign = TextAlign.Justify,
        )
    }
}

/**
 * Disponibilité en France, d'après les données JustWatch servies par TMDB (le crédit à JustWatch
 * est une condition d'utilisation de ces données). La ligne location/achat ne concerne que les
 * films : pour une série, l'intérêt est de savoir où la regarder, pas où acheter ses épisodes.
 */
@Composable
private fun AvailabilitySection(availability: WatchAvailability, type: MediaType) {
    val known = availability as? WatchAvailability.Known
    Column {
        SectionLabel(stringResource(R.string.detail_availability_label))
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AvailabilityLine(
                label = stringResource(R.string.detail_availability_streaming),
                providers = known?.streaming.orEmpty(),
                isKnown = known != null,
            )
            if (type == MediaType.FILM) {
                AvailabilityLine(
                    label = stringResource(R.string.detail_availability_rent_buy),
                    providers = known?.rentOrBuy.orEmpty(),
                    isKnown = known != null,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        JustWatchCredit(link = known?.link)
    }
}

@Composable
private fun AvailabilityLine(label: String, providers: List<WatchProvider>, isKnown: Boolean) {
    Column {
        Text(text = label, style = PelliculeTextStyles.detailSubtitle, color = TextTertiary)
        Spacer(modifier = Modifier.height(8.dp))
        if (providers.isEmpty()) {
            Text(
                // Aucune offre connue en France et donnée fiable : c'est une absence, pas une
                // ignorance — les deux cas ne se disent pas de la même façon.
                text = stringResource(
                    if (isKnown) R.string.detail_availability_none else R.string.detail_availability_unknown,
                ),
                style = PelliculeTextStyles.detailSubtitle,
                color = TextMuted,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                providers.forEach { provider -> ProviderChip(provider = provider) }
            }
        }
    }
}

@Composable
private fun ProviderChip(provider: WatchProvider) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (provider.logoUrl != null) {
            AsyncImage(
                model = provider.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
        }
        Text(text = provider.name, style = PelliculeTextStyles.cardSubtitle, color = TextPrimary)
    }
}

@Composable
private fun JustWatchCredit(link: String?) {
    val uriHandler = LocalUriHandler.current
    val style = PelliculeTextStyles.linkLabel
    Text(
        text = stringResource(R.string.detail_availability_justwatch),
        style = if (link != null) style.copy(textDecoration = TextDecoration.Underline) else style,
        color = TextMuted,
        modifier = if (link != null) Modifier.clickable { uriHandler.openUri(link) } else Modifier,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text.uppercase(Locale.FRENCH), style = PelliculeTextStyles.sectionLabel, color = TextMuted)
}

@Composable
private fun StatusSection(selected: WatchStatus, onStatusSelected: (WatchStatus) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.detail_status_label))
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            WatchStatus.entries.forEach { status ->
                StatusPill(status = status, selected = status == selected, onClick = { onStatusSelected(status) })
            }
        }
    }
}

@Composable
private fun StatusPill(status: WatchStatus, selected: Boolean, onClick: () -> Unit) {
    val palette = status.palette()
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) {
                    Modifier.background(palette.color)
                } else {
                    Modifier
                        .background(palette.badgeBackground)
                        .border(BorderStroke(0.5.dp, palette.badgeBorder), RoundedCornerShape(20.dp))
                },
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(status.labelRes()),
            style = PelliculeTextStyles.statusPillLabel,
            color = if (selected) BackgroundDark else palette.color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RatingSection(rating: Int?, onRatingSelected: (Int?) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.detail_rating_label))
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            for (star in 1..5) {
                val filled = rating != null && star <= rating
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        // Recliquer sur l'étoile qui correspond déjà à la note actuelle efface la
                        // note (ex. contenu noté 1 étoile : cliquer à nouveau sur la 1ère étoile
                        // revient à "aucune note") plutôt que de la reconfirmer sans effet visible.
                        .clickable(onClick = { onRatingSelected(if (rating == star) null else star) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = stringResource(R.string.detail_rating_star_content_description, star),
                        tint = if (filled) AccentPurple else AccentPurpleMuted,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodesSection(
    seasons: List<Season>,
    selectedSeasonNumber: Int?,
    episodes: List<EpisodeUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeWatchedToggled: (EpisodeUiModel) -> Unit,
) {
    Column {
        SectionLabel(stringResource(R.string.detail_episodes_label))
        Spacer(modifier = Modifier.height(10.dp))
        if (seasons.size > 1) {
            SeasonChipsRow(seasons = seasons, selectedSeasonNumber = selectedSeasonNumber, onSeasonSelected = onSeasonSelected)
            Spacer(modifier = Modifier.height(12.dp))
        }
        when {
            isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPurple)
            }
            errorMessage != null -> Text(text = errorMessage, style = PelliculeTextStyles.emptyMessage, color = TextMuted)
            episodes.isEmpty() -> Text(
                text = stringResource(R.string.detail_episodes_empty),
                style = PelliculeTextStyles.emptyMessage,
                color = TextMuted,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                episodes.forEach { episode ->
                    EpisodeRow(episode = episode, onClick = { onEpisodeWatchedToggled(episode) })
                }
            }
        }
    }
}

@Composable
private fun SeasonChipsRow(seasons: List<Season>, selectedSeasonNumber: Int?, onSeasonSelected: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        seasons.forEach { season ->
            SeasonChip(
                season = season,
                selected = season.seasonNumber == selectedSeasonNumber,
                onClick = { onSeasonSelected(season.seasonNumber) },
            )
        }
    }
}

@Composable
private fun SeasonChip(season: Season, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(AccentPurple)
                else Modifier.border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp))
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.filter_chip_label, season.name, season.episodeCount),
            style = PelliculeTextStyles.chipLabel,
            color = if (selected) TextPrimary else TextTertiary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeUiModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EpisodeThumbnail(stillUrl = episode.stillUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.detail_episode_number_label, episode.episodeNumber),
                style = PelliculeTextStyles.cardSubtitle,
                color = TextMuted,
            )
            Text(
                text = episode.title,
                style = PelliculeTextStyles.cardTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (episode.watched) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = stringResource(
                if (episode.watched) R.string.detail_episode_mark_unwatched else R.string.detail_episode_mark_watched,
            ),
            tint = if (episode.watched) SuccessGreen else TextMuted,
        )
    }
}

@Composable
private fun EpisodeThumbnail(stillUrl: String?) {
    // Taille fixe (comme MediaCoverPlaceholder en carte liste) plutôt qu'un aspectRatio : dans un
    // Row sans poids, la largeur n'est pas bornée et aspectRatio n'aurait rien à calculer.
    val modifier = Modifier
        .size(width = 80.dp, height = 45.dp)
        .clip(RoundedCornerShape(6.dp))

    if (stillUrl != null) {
        AsyncImage(model = stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier = modifier.background(SurfaceCard))
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AccentPurple)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_add_action),
            style = PelliculeTextStyles.statusPillLabel,
            color = TextPrimary,
        )
    }
}

@Composable
private fun RemoveLink(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.detail_remove_action),
            style = PelliculeTextStyles.linkLabel.copy(textDecoration = TextDecoration.Underline),
            color = ErrorCoral,
        )
    }
}

@Composable
private fun RemoveConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextTertiary,
        title = { Text(stringResource(R.string.detail_remove_confirm_title)) },
        text = { Text(stringResource(R.string.detail_remove_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.detail_remove_confirm_confirm), color = ErrorCoral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.detail_remove_confirm_cancel), color = TextTertiary)
            }
        },
    )
}

@Composable
private fun StatusResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextTertiary,
        title = { Text(stringResource(R.string.detail_status_reset_confirm_title)) },
        text = { Text(stringResource(R.string.detail_status_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.detail_status_reset_confirm_confirm), color = ErrorCoral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.detail_status_reset_confirm_cancel), color = TextTertiary)
            }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun DetailContentPreview() {
    val media = Media(
        id = "1",
        title = "Perfect Blue",
        type = MediaType.ANIME,
        status = WatchStatus.EN_COURS,
        releaseYear = 1997,
    )
    val episodes = listOf(
        EpisodeUiModel(seasonNumber = 1, episodeNumber = 1, title = "Premier épisode", stillUrl = null, watched = true),
        EpisodeUiModel(seasonNumber = 1, episodeNumber = 2, title = "Deuxième épisode", stillUrl = null, watched = false),
    )
    PelliculeTheme {
        DetailContent(
            media = media,
            isInBacklog = true,
            synopsis = "Une danseuse d'un groupe de pop japonais se lance dans une carrière d'actrice…",
            watchAvailability = WatchAvailability.Known(
                streaming = listOf(WatchProvider(id = 8, name = "Netflix", logoUrl = null)),
                rentOrBuy = emptyList(),
                link = "https://www.themoviedb.org/movie/10494/watch?locale=FR",
            ),
            seasons = listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 2, posterUrl = null)),
            selectedSeasonNumber = 1,
            episodes = episodes,
            episodesLoading = false,
            episodesErrorMessage = null,
            onBackClick = {},
            onStatusSelected = {},
            onRatingSelected = {},
            onSeasonSelected = {},
            onEpisodeWatchedToggled = {},
            onRemoveMedia = {},
            onAddMedia = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun DetailContentApercuPreview() {
    val media = Media(title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.A_VOIR, releaseYear = 1997)
    PelliculeTheme {
        DetailContent(
            media = media,
            isInBacklog = false,
            synopsis = null,
            watchAvailability = WatchAvailability.Unknown,
            seasons = emptyList(),
            selectedSeasonNumber = null,
            episodes = emptyList(),
            episodesLoading = false,
            episodesErrorMessage = null,
            onBackClick = {},
            onStatusSelected = {},
            onRatingSelected = {},
            onSeasonSelected = {},
            onEpisodeWatchedToggled = {},
            onRemoveMedia = {},
            onAddMedia = {},
        )
    }
}
