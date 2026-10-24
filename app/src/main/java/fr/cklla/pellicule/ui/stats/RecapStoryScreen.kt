package fr.cklla.pellicule.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.domain.recap.RecapSlide
import fr.cklla.pellicule.domain.recap.buildRecapSlides
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.color
import java.time.ZoneId

/**
 * Récap en images d'une année : un pager horizontal de slides plein écran (voir
 * [RecapStoryViewModel]). Les grilles défilent verticalement à l'intérieur de leur slide, jamais en
 * horizontal, pour ne pas se disputer le balayage avec le pager. Un tap sur une affiche ouvre la
 * fiche Détail.
 */
@Composable
fun RecapStoryScreen(
    onCloseClick: () -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapStoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecapStoryContent(
        year = viewModel.year,
        state = state,
        onCloseClick = onCloseClick,
        onMediaClick = onMediaClick,
        modifier = modifier,
    )
}

@Composable
private fun RecapStoryContent(
    year: Int,
    state: RecapStoryUiState,
    onCloseClick: () -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(LocalRecapReducedMotion provides reducedMotion) {
        val chromeAlpha = rememberChromeAlpha(reducedMotion)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundDark),
        ) {
            when {
                state.isLoading -> StoryTopBar(slideCount = 0, currentPage = 0, chromeAlpha = chromeAlpha, onCloseClick = onCloseClick)
                state.slides.isEmpty() -> {
                    StoryTopBar(slideCount = 0, currentPage = 0, chromeAlpha = chromeAlpha, onCloseClick = onCloseClick)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.recap_story_empty, year),
                            style = PelliculeTextStyles.emptyMessage,
                            color = TextMuted,
                        )
                    }
                }
                else -> StoryPager(
                    year = year,
                    slides = state.slides,
                    chromeAlpha = chromeAlpha,
                    onCloseClick = onCloseClick,
                    onMediaClick = onMediaClick,
                )
            }
        }
    }
}

/**
 * Le pager n'est créé qu'une fois les slides connues : son état (page courante) est conservé par
 * `rememberPagerState` à travers une rotation et un aller-retour vers une fiche.
 */
@Composable
private fun ColumnScope.StoryPager(
    year: Int,
    slides: List<RecapSlide>,
    chromeAlpha: () -> Float,
    onCloseClick: () -> Unit,
    onMediaClick: (String) -> Unit,
) {
    val pagerState = rememberPagerState { slides.size }
    val reducedMotion = LocalRecapReducedMotion.current
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = RecapMotion.PAGE_SNAP_DURATION, easing = RecapMotion.EaseOut)
        },
    )
    StoryTopBar(slideCount = slides.size, currentPage = pagerState.currentPage, chromeAlpha = chromeAlpha, onCloseClick = onCloseClick)
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        key = { index -> slides[index].key },
        flingBehavior = flingBehavior,
    ) { index ->
        val slide = slides[index]
        val clock = rememberRecapEntryClock()
        val pageOffset = { pagerState.pageOffset(index) }
        CompositionLocalProvider(LocalRecapEntryClock provides clock) {
            Box(modifier = Modifier.fillMaxSize().recapPageFade(reducedMotion, pageOffset)) {
                RecapSlideBackground(
                    tint = slideTint(slide),
                    clock = clock,
                    reducedMotion = reducedMotion,
                    pageOffset = pageOffset,
                )
                RecapSlideContent(slide = slide, year = year, onMediaClick = onMediaClick)
            }
        }
    }
}

/** Couleur du voile et du halo d'une slide : celle du type pour les coups de cœur, le violet sinon. */
private fun slideTint(slide: RecapSlide): Color =
    (slide as? RecapSlide.TypeFavorites)?.type?.color() ?: AccentPurple

/** Opacité du bouton de fermeture et de l'indicateur : ils apparaissent en fondu une seule fois à l'ouverture. */
@Composable
private fun rememberChromeAlpha(reducedMotion: Boolean): () -> Float {
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = RecapMotion.CHROME_FADE_DURATION,
                    delayMillis = RecapMotion.CHROME_FADE_DELAY,
                    easing = RecapMotion.EaseOut,
                ),
            )
        }
    }
    return { alpha.value }
}

@Composable
private fun StoryTopBar(slideCount: Int, currentPage: Int, chromeAlpha: () -> Float, onCloseClick: () -> Unit) {
    val progress = stringResource(R.string.recap_story_progress, currentPage + 1, slideCount)
    Column(
        modifier = Modifier
            .graphicsLayer { alpha = chromeAlpha() }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        if (slideCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .semantics { contentDescription = progress },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(slideCount) { index ->
                    StorySegment(filled = index <= currentPage, startsFilled = index < currentPage, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recap_story_close),
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Segment de l'indicateur : se remplit horizontalement quand sa slide devient la slide courante. */
@Composable
private fun StorySegment(filled: Boolean, startsFilled: Boolean, modifier: Modifier = Modifier) {
    val reducedMotion = LocalRecapReducedMotion.current
    val fill = remember { Animatable(if (startsFilled) 1f else 0f) }
    LaunchedEffect(filled, reducedMotion) {
        val target = if (filled) 1f else 0f
        if (reducedMotion) {
            fill.snapTo(target)
        } else {
            fill.animateTo(target, tween(durationMillis = RecapMotion.SEGMENT_FILL_DURATION, easing = RecapMotion.EaseOut))
        }
    }
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(BorderHairline)
            .drawBehind { drawRect(AccentPurple, size = Size(size.width * fill.value, size.height)) },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapStoryContentPreview() {
    val watchedAt = 1_790_000_000_000L
    val media = listOf(
        Media(id = "1", title = "Dune", type = MediaType.FILM, status = WatchStatus.VU, rating = 5, watchedAt = watchedAt, releaseYear = 2021),
        Media(id = "2", title = "Severance", type = MediaType.SERIE, status = WatchStatus.VU, rating = 4, watchedAt = watchedAt + 86_400_000L, releaseYear = 2022),
        Media(id = "3", title = "Perfect Blue", type = MediaType.ANIME, status = WatchStatus.VU, rating = 5, watchedAt = watchedAt + 172_800_000L, releaseYear = 1997),
    )
    PelliculeTheme {
        RecapStoryContent(
            year = 2026,
            state = RecapStoryUiState(isLoading = false, slides = buildRecapSlides(media, 2026, ZoneId.systemDefault())),
            onCloseClick = {},
            onMediaClick = {},
        )
    }
}

