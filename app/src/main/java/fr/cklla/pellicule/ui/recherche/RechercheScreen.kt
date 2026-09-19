package fr.cklla.pellicule.ui.recherche

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.MediaSearchResult
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.ui.components.MediaCoverPlaceholder
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.SuccessGreen
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextSecondary

@Composable
fun RechercheScreen(
    modifier: Modifier = Modifier,
    viewModel: RechercheViewModel = hiltViewModel(),
    onResultClick: (MediaSearchResult, trackedMediaId: String?) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RechercheContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onAddMedia = viewModel::onAddMedia,
        onResultClick = onResultClick,
        modifier = modifier,
    )
}

@Composable
private fun RechercheContent(
    uiState: RechercheUiState,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onAddMedia: (MediaSearchResult) -> Unit,
    onResultClick: (MediaSearchResult, trackedMediaId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        Header()
        SearchBar(query = uiState.query, onQueryChanged = onQueryChanged, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(modifier = Modifier.height(16.dp))
        when {
            uiState.query.isBlank() -> SuggestionsSection(onSuggestionSelected = onSuggestionSelected)
            uiState.isSearching -> LoadingState()
            uiState.errorMessage != null -> ErrorState(message = uiState.errorMessage)
            uiState.results.isEmpty() -> NoResultsState(query = uiState.query)
            else -> ResultsList(
                results = uiState.results,
                trackedMediaIdsByTmdbId = uiState.trackedMediaIdsByTmdbId,
                onAddMedia = onAddMedia,
                onResultClick = onResultClick,
            )
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
        Text(text = stringResource(R.string.recherche_kicker), style = PelliculeTextStyles.kicker, color = AccentPurpleLight)
        Text(text = stringResource(R.string.recherche_title), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
    }
}

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = Icons.Outlined.Search, contentDescription = null, tint = TextMuted)
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = PelliculeTextStyles.chipLabel.copy(color = TextPrimary),
                cursorBrush = SolidColor(AccentPurple),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(text = stringResource(R.string.recherche_search_placeholder), style = PelliculeTextStyles.chipLabel, color = AccentPurpleMuted)
                    }
                    innerTextField()
                },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChanged("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.recherche_clear_search_content_description),
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SuggestionsSection(onSuggestionSelected: (String) -> Unit) {
    val suggestions = stringArrayResource(R.array.recherche_suggestions)
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.recherche_suggestions_label).uppercase(),
            style = PelliculeTextStyles.sectionLabel,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                SuggestionChip(label = suggestion, onClick = { onSuggestionSelected(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.7f)), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = PelliculeTextStyles.chipLabel, color = TextSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentPurple)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(text = message, style = PelliculeTextStyles.emptyMessage, color = TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoResultsState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.5.dp, AccentPurpleMuted), CircleShape),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.recherche_empty_title), style = PelliculeTextStyles.emptyTitle, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recherche_empty_message, query),
            style = PelliculeTextStyles.emptyMessage,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultsList(
    results: List<MediaSearchResult>,
    trackedMediaIdsByTmdbId: Map<Long, String>,
    onAddMedia: (MediaSearchResult) -> Unit,
    onResultClick: (MediaSearchResult, trackedMediaId: String?) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = results, key = { it.tmdbId }) { result ->
            val trackedMediaId = trackedMediaIdsByTmdbId[result.tmdbId]
            ResultRow(
                result = result,
                trackedMediaId = trackedMediaId,
                onAddMedia = onAddMedia,
                onClick = { onResultClick(result, trackedMediaId) },
            )
        }
    }
}

@Composable
private fun ResultRow(
    result: MediaSearchResult,
    trackedMediaId: String?,
    onAddMedia: (MediaSearchResult) -> Unit,
    onClick: () -> Unit,
) {
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
        MediaCoverPlaceholder(title = result.title, width = 48.dp, height = 64.dp, posterUrl = result.posterUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = result.title, style = PelliculeTextStyles.cardTitle, color = TextPrimary, maxLines = 2)
            Text(
                text = stringResource(
                    R.string.media_card_type_year,
                    stringResource(result.type.labelRes()),
                    result.year?.toString() ?: stringResource(R.string.media_card_year_unknown),
                ),
                style = PelliculeTextStyles.cardSubtitle,
                color = TextMuted,
            )
        }
        if (trackedMediaId != null) {
            AddedPill()
        } else {
            AddButton(title = result.title, onClick = { onAddMedia(result) })
        }
    }
}

@Composable
private fun AddedPill() {
    Box(
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SuccessGreen.copy(alpha = 0.12f))
            .border(BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.4f)), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.recherche_added_label),
            style = PelliculeTextStyles.badgeLabel,
            color = SuccessGreen,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AddButton(title: String, onClick: () -> Unit) {
    Box(
        // Zone de clic 48dp (accessibilité) autour du bouton visuel 30dp de la maquette.
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AccentPurple),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.recherche_add_content_description, title),
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RechercheSuggestionsPreview() {
    PelliculeTheme {
        RechercheContent(
            uiState = RechercheUiState(),
            onQueryChanged = {},
            onSuggestionSelected = {},
            onAddMedia = {},
            onResultClick = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RechercheResultsPreview() {
    val results = listOf(
        MediaSearchResult(tmdbId = 1, title = "Perfect Blue", type = MediaType.ANIME, year = 1997, posterUrl = null),
        MediaSearchResult(tmdbId = 2, title = "Dune", type = MediaType.FILM, year = 2021, posterUrl = null),
    )
    PelliculeTheme {
        RechercheContent(
            uiState = RechercheUiState(query = "du", results = results),
            onQueryChanged = {},
            onSuggestionSelected = {},
            onAddMedia = {},
            onResultClick = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RechercheEmptyPreview() {
    PelliculeTheme {
        RechercheContent(
            uiState = RechercheUiState(query = "zzzzz"),
            onQueryChanged = {},
            onSuggestionSelected = {},
            onAddMedia = {},
            onResultClick = { _, _ -> },
        )
    }
}
