package fr.cklla.pellicule.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.components.TypeChipsRow
import fr.cklla.pellicule.ui.components.YearChipsRow
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextSecondary
import fr.cklla.pellicule.ui.theme.color
import java.util.Locale

@Composable
fun StatsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(
        uiState = uiState,
        onYearSelected = viewModel::onYearSelected,
        onTypeSelected = viewModel::onTypeSelected,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
private fun StatsContent(
    uiState: StatsUiState,
    onYearSelected: (Int?) -> Unit,
    onTypeSelected: (MediaType?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        StatsBackHeader(label = stringResource(R.string.stats_back), onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
        ) {
            Text(text = stringResource(R.string.stats_kicker), style = PelliculeTextStyles.kicker, color = AccentPurpleLight)
            Text(text = stringResource(R.string.stats_title), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
        }
        if (uiState.availableYears.isNotEmpty()) {
            YearChipsRow(
                years = uiState.availableYears,
                selectedYear = uiState.stats.selectedYear,
                onYearSelected = onYearSelected,
            )
        }
        TypeChipsRow(selectedType = uiState.stats.selectedType, onTypeSelected = onTypeSelected)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TypeDonutChart(stats = uiState.stats)
            TypeLegend(stats = uiState.stats)
        }
    }
}

/**
 * Anneau de répartition par type : un segment par type, dans l'ordre de [MediaType.entries], en
 * partant du haut dans le sens horaire. Anneau gris de bordure quand il n'y a rien à répartir.
 * Quand un type est sélectionné, son segment reste plein et les autres sont atténués ; le nombre
 * au centre est celui de la sélection.
 */
@Composable
internal fun TypeDonutChart(stats: StatsData, modifier: Modifier = Modifier) {
    val segments = donutSegments(stats.countsByType)
    Box(modifier = modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 23.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            val arcSize = Size(diameter, diameter)
            if (segments.isEmpty()) {
                drawArc(
                    color = BorderHairline,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx),
                )
            } else {
                segments.forEach { segment ->
                    val dimmed = stats.selectedType != null && stats.selectedType != segment.type
                    drawArc(
                        color = segment.type.color().copy(alpha = if (dimmed) DIMMED_ALPHA else 1f),
                        startAngle = segment.startAngle,
                        sweepAngle = segment.sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stats.watchedCount.toString(), style = PelliculeTextStyles.donutValue, color = TextPrimary)
            Text(
                text = stringResource(R.string.stats_donut_label).uppercase(Locale.FRENCH),
                style = PelliculeTextStyles.donutLabel,
                color = TextMuted,
            )
        }
    }
}

private const val DIMMED_ALPHA = 0.3f

@Composable
internal fun TypeLegend(stats: StatsData, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaType.entries.forEach { type ->
            val dimmed = stats.selectedType != null && stats.selectedType != type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(type.color().copy(alpha = if (dimmed) DIMMED_ALPHA else 1f)),
                )
                Text(
                    text = stringResource(type.labelRes()),
                    style = PelliculeTextStyles.legendLabel,
                    color = if (dimmed) TextMuted else TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (stats.countsByType[type] ?: 0).toString(),
                    style = PelliculeTextStyles.legendCount,
                    color = if (dimmed) TextMuted else TextPrimary,
                )
            }
        }
    }
}

private val previewMedia = listOf(
    Media(id = "1", title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, watchedAt = 1_726_000_000_000L),
    Media(id = "2", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = 1_726_000_000_000L),
    Media(id = "3", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU, watchedAt = 1_694_000_000_000L),
    Media(id = "4", title = "Arcane", type = MediaType.SERIE, status = WatchStatus.VU, watchedAt = 1_726_000_000_000L),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun StatsContentPreview() {
    PelliculeTheme {
        StatsContent(
            uiState = StatsUiState(stats = computeStats(previewMedia), availableYears = listOf(2024, 2023)),
            onYearSelected = {},
            onTypeSelected = {},
            onBackClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun StatsContentEmptyPreview() {
    PelliculeTheme {
        StatsContent(uiState = StatsUiState(), onYearSelected = {}, onTypeSelected = {}, onBackClick = {})
    }
}
