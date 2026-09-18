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
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.components.MediaCoverPlaceholder
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
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
        seasons = uiState.seasons,
        selectedSeasonNumber = uiState.selectedSeasonNumber,
        episodes = uiState.episodes,
        episodesLoading = uiState.episodesLoading,
        episodesErrorMessage = uiState.episodesErrorMessage,
        onBackClick = onBackClick,
        onStatusSelected = viewModel::onStatusSelected,
        onSeasonSelected = viewModel::onSeasonSelected,
        onEpisodeWatchedToggled = viewModel::onEpisodeWatchedToggled,
        onRemoveMedia = viewModel::onRemoveMedia,
        modifier = modifier,
    )
}

@Composable
private fun DetailContent(
    media: Media,
    seasons: List<Season>,
    selectedSeasonNumber: Int?,
    episodes: List<EpisodeUiModel>,
    episodesLoading: Boolean,
    episodesErrorMessage: String?,
    onBackClick: () -> Unit,
    onStatusSelected: (WatchStatus) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeWatchedToggled: (EpisodeUiModel) -> Unit,
    onRemoveMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }

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
            StatusSection(selected = media.status, onStatusSelected = onStatusSelected)
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
            seasons = listOf(Season(seasonNumber = 1, name = "Saison 1", episodeCount = 2, posterUrl = null)),
            selectedSeasonNumber = 1,
            episodes = episodes,
            episodesLoading = false,
            episodesErrorMessage = null,
            onBackClick = {},
            onStatusSelected = {},
            onSeasonSelected = {},
            onEpisodeWatchedToggled = {},
            onRemoveMedia = {},
        )
    }
}
