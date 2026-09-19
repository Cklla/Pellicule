package fr.cklla.pellicule.ui.bibliotheque

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.components.MediaCoverPlaceholder
import fr.cklla.pellicule.ui.components.StatusBadge
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextSecondary
import fr.cklla.pellicule.ui.theme.TextTertiary

@Composable
fun BibliothequeScreen(
    modifier: Modifier = Modifier,
    onMediaClick: (String) -> Unit = {},
    viewModel: BibliothequeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BibliothequeContent(
        uiState = uiState,
        onFilterSelected = viewModel::onFilterSelected,
        onWatchedYearSelected = viewModel::onWatchedYearSelected,
        onTypeSelected = viewModel::onTypeSelected,
        onMediaClick = onMediaClick,
        modifier = modifier,
    )
}

@Composable
private fun BibliothequeContent(
    uiState: BibliothequeUiState,
    onFilterSelected: (BibliothequeFilter) -> Unit,
    onWatchedYearSelected: (Int?) -> Unit,
    onTypeSelected: (MediaType?) -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        Header()
        FilterChipsRow(
            selectedFilter = uiState.selectedFilter,
            counts = uiState.filterCounts,
            onFilterSelected = onFilterSelected,
        )
        TypeChipsRow(
            selectedType = uiState.selectedType,
            onTypeSelected = onTypeSelected,
        )
        if (uiState.selectedFilter == BibliothequeFilter.VU && uiState.availableWatchedYears.isNotEmpty()) {
            YearChipsRow(
                years = uiState.availableWatchedYears,
                selectedYear = uiState.selectedWatchedYear,
                onYearSelected = onWatchedYearSelected,
            )
        }
        if (uiState.visibleMedia.isEmpty()) {
            EmptyState(filter = uiState.selectedFilter, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.visibleMedia, key = { it.id }) { media ->
                    MediaCard(media = media, onClick = { onMediaClick(media.id) })
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.bibliotheque_kicker),
            style = PelliculeTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(
            text = stringResource(R.string.bibliotheque_title),
            style = PelliculeTextStyles.screenTitle,
            color = TextPrimary,
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: BibliothequeFilter,
    counts: Map<BibliothequeFilter, Int>,
    onFilterSelected: (BibliothequeFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = BibliothequeFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                filter = filter,
                count = counts[filter] ?: 0,
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterChip(filter: BibliothequeFilter, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(AccentPurple)
                else Modifier.border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.filter_chip_label, stringResource(filter.labelRes), count),
            style = PelliculeTextStyles.chipLabel.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) TextPrimary else TextTertiary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun YearChipsRow(
    years: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SecondaryChip(
                label = stringResource(R.string.filter_annee_toutes),
                selected = selectedYear == null,
                onClick = { onYearSelected(null) },
            )
        }
        items(items = years, key = { it }) { year ->
            SecondaryChip(
                label = year.toString(),
                selected = year == selectedYear,
                onClick = { onYearSelected(year) },
            )
        }
    }
}

@Composable
private fun SecondaryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.background(AccentPurpleMuted)
                else Modifier.border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PelliculeTextStyles.chipLabel,
            color = if (selected) TextPrimary else TextTertiary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TypeChipsRow(
    selectedType: MediaType?,
    onTypeSelected: (MediaType?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SecondaryChip(
                label = stringResource(R.string.filter_type_tous),
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
            )
        }
        items(items = MediaType.entries, key = { it.name }) { type ->
            SecondaryChip(
                label = stringResource(type.labelRes()),
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
            )
        }
    }
}

@Composable
private fun MediaCard(media: Media, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaCoverPlaceholder(
            title = media.title,
            width = 56.dp,
            height = 76.dp,
            posterUrl = media.posterUrl,
            letterStyle = PelliculeTextStyles.coverLetterListCard,
        )
        Column {
            Text(
                text = media.title,
                style = PelliculeTextStyles.cardTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.media_card_type_year,
                    stringResource(media.type.labelRes()),
                    media.releaseYear?.toString() ?: stringResource(R.string.media_card_year_unknown),
                ),
                style = PelliculeTextStyles.cardSubtitle,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusBadge(status = media.status)
        }
    }
}

@Composable
private fun EmptyState(filter: BibliothequeFilter, modifier: Modifier = Modifier) {
    val messageRes = when (filter) {
        BibliothequeFilter.TOUS -> R.string.empty_message_tous
        BibliothequeFilter.A_VOIR -> R.string.empty_message_a_voir
        BibliothequeFilter.EN_COURS -> R.string.empty_message_en_cours
        BibliothequeFilter.VU -> R.string.empty_message_vu
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(BorderStroke(1.5.dp, AccentPurpleMuted), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .rotate(45f)
                    .border(BorderStroke(1.5.dp, AccentPurpleMuted)),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.empty_title), style = PelliculeTextStyles.emptyTitle, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(messageRes),
            style = PelliculeTextStyles.emptyMessage,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun BibliothequeContentPreview() {
    val media = listOf(
        Media(id = "1", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.EN_COURS, releaseYear = 1997),
        Media(id = "2", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, releaseYear = 2022, watchedAt = 1_726_000_000_000L),
        Media(id = "3", title = "Dune", type = MediaType.FILM, status = WatchStatus.A_VOIR, releaseYear = 2021),
    )
    PelliculeTheme {
        BibliothequeContent(
            uiState = BibliothequeUiState(
                isLoading = false,
                visibleMedia = media,
                selectedFilter = BibliothequeFilter.TOUS,
                filterCounts = countByFilter(media),
                availableWatchedYears = availableWatchedYears(media),
            ),
            onFilterSelected = {},
            onWatchedYearSelected = {},
            onTypeSelected = {},
            onMediaClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun BibliothequeVuAvecAnneesPreview() {
    val media = listOf(
        Media(id = "1", title = "Game of Thrones", type = MediaType.SERIE, status = WatchStatus.VU, releaseYear = 2011, watchedAt = 1_726_000_000_000L),
        Media(id = "2", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, releaseYear = 2022, watchedAt = 1_726_000_000_000L),
        Media(id = "3", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU, releaseYear = 1997, watchedAt = 1_694_000_000_000L),
    )
    PelliculeTheme {
        BibliothequeContent(
            uiState = BibliothequeUiState(
                isLoading = false,
                visibleMedia = media,
                selectedFilter = BibliothequeFilter.VU,
                filterCounts = countByFilter(media),
                availableWatchedYears = availableWatchedYears(media),
                selectedWatchedYear = null,
            ),
            onFilterSelected = {},
            onWatchedYearSelected = {},
            onTypeSelected = {},
            onMediaClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun BibliothequeEmptyPreview() {
    PelliculeTheme {
        BibliothequeContent(
            uiState = BibliothequeUiState(isLoading = false, selectedFilter = BibliothequeFilter.VU),
            onFilterSelected = {},
            onWatchedYearSelected = {},
            onTypeSelected = {},
            onMediaClick = {},
        )
    }
}
