package fr.cklla.pellicule.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.SuccessGreen
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextTertiary
import fr.cklla.pellicule.ui.theme.color

/**
 * Bilan d'une année : total de contenus vus et répartition par type, calculés par [computeStats]
 * sur l'année ciblée. Le total et chaque compteur ouvrent la liste correspondante (voir
 * [RecapMediaScreen]) ; `null` désigne tous les types.
 */
@Composable
fun RecapScreen(
    onBackClick: () -> Unit,
    onMediaListClick: (year: Int, type: MediaType?) -> Unit,
    onStoryClick: (year: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val stats by viewModel.uiState.collectAsStateWithLifecycle()
    RecapContent(
        year = viewModel.year,
        stats = stats,
        onBackClick = onBackClick,
        onMediaListClick = { type -> onMediaListClick(viewModel.year, type) },
        onStoryClick = { onStoryClick(viewModel.year) },
        modifier = modifier,
    )
}

@Composable
private fun RecapContent(
    year: Int,
    stats: StatsData,
    onBackClick: () -> Unit,
    onMediaListClick: (MediaType?) -> Unit,
    onStoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        StatsBackHeader(label = stringResource(R.string.recap_back), onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column {
                Text(text = stringResource(R.string.recap_kicker), style = PelliculeTextStyles.kicker, color = AccentPurpleLight)
                Text(text = stringResource(R.string.recap_title, year), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
            }
            Button(
                onClick = onStoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = TextPrimary),
            ) {
                Text(text = stringResource(R.string.recap_story_button), style = PelliculeTextStyles.statusPillLabel)
            }
            RecapHeroCard(stats = stats, onMediaListClick = onMediaListClick)
        }
    }
}

/**
 * Total mis en avant (gros chiffre central) puis un compteur par type. Les valeurs viennent telles
 * quelles de [stats], aucun calcul ici. Total et compteurs sont cliquables, désactivés à 0 : inutile
 * d'ouvrir une liste qu'on sait vide.
 */
@Composable
private fun RecapHeroCard(stats: StatsData, onMediaListClick: (MediaType?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = stats.watchedCount > 0) { onMediaListClick(null) }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stats.watchedCount.toString(), style = PelliculeTextStyles.donutValue, color = SuccessGreen)
            Text(text = stringResource(R.string.recap_total_label), style = PelliculeTextStyles.cardSubtitle, color = TextTertiary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = BorderHairline.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MediaType.entries.forEach { type ->
                val count = stats.countsByType[type] ?: 0
                RecapTypeStat(
                    value = count.toString(),
                    label = stringResource(type.labelRes()),
                    valueColor = type.color(),
                    onClick = if (count > 0) ({ onMediaListClick(type) }) else null,
                )
            }
        }
    }
}

@Composable
private fun RecapTypeStat(value: String, label: String, valueColor: Color, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = PelliculeTextStyles.statValueMedium, color = valueColor)
        Text(text = label, style = PelliculeTextStyles.cardSubtitle, color = TextTertiary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapContentPreview() {
    val media = listOf(
        Media(id = "1", title = "Dune", type = MediaType.FILM, status = WatchStatus.VU),
        Media(id = "2", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU),
        Media(id = "3", title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU),
    )
    PelliculeTheme {
        RecapContent(
            year = 2026,
            stats = computeStats(media.map { it.copy(watchedAt = 1_790_000_000_000L) }, selectedYear = 2026),
            onBackClick = {},
            onMediaListClick = {},
            onStoryClick = {},
        )
    }
}
