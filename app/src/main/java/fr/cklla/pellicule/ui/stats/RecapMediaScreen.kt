package fr.cklla.pellicule.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.components.MediaCard
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary

/** Liste des contenus vus d'une année (voir [RecapMediaViewModel]) ; un tap ouvre la fiche Détail. */
@Composable
fun RecapMediaScreen(
    onBackClick: () -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapMediaViewModel = hiltViewModel(),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    RecapMediaContent(
        year = viewModel.year,
        type = viewModel.type,
        media = media,
        onBackClick = onBackClick,
        onMediaClick = onMediaClick,
        modifier = modifier,
    )
}

@Composable
private fun RecapMediaContent(
    year: Int,
    type: MediaType?,
    media: List<Media>,
    onBackClick: () -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (type) {
        null -> R.string.recap_media_title_all
        MediaType.FILM -> R.string.recap_media_title_film
        MediaType.SERIE -> R.string.recap_media_title_serie
        MediaType.ANIME -> R.string.recap_media_title_anime
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        StatsBackHeader(label = stringResource(R.string.recap_media_back), onBackClick = onBackClick)
        Column(modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp)) {
            Text(text = stringResource(titleRes, year), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
        }
        if (media.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = stringResource(R.string.recap_media_empty), style = PelliculeTextStyles.emptyMessage, color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = media, key = { it.id }) { item ->
                    MediaCard(media = item, onClick = { onMediaClick(item.id) })
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapMediaContentPreview() {
    val media = listOf(
        Media(id = "1", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, releaseYear = 2022),
        Media(id = "2", title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, releaseYear = 2021),
    )
    PelliculeTheme {
        RecapMediaContent(year = 2026, type = MediaType.SERIE, media = media, onBackClick = {}, onMediaClick = {})
    }
}
