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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.components.MediaCoverPlaceholder
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.ErrorCoral
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
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
        onBackClick = onBackClick,
        onStatusSelected = viewModel::onStatusSelected,
        onRemoveMedia = viewModel::onRemoveMedia,
        modifier = modifier,
    )
}

@Composable
private fun DetailContent(
    media: Media,
    onBackClick: () -> Unit,
    onStatusSelected: (WatchStatus) -> Unit,
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
    PelliculeTheme {
        DetailContent(
            media = media,
            onBackClick = {},
            onStatusSelected = {},
            onRemoveMedia = {},
        )
    }
}
