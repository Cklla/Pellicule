package fr.cklla.pellicule.ui.stats

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.recap.BusiestMonth
import fr.cklla.pellicule.domain.recap.DatedMedia
import fr.cklla.pellicule.domain.recap.RecapSlide
import fr.cklla.pellicule.domain.recap.formatRecapDate
import fr.cklla.pellicule.domain.recap.frenchName
import fr.cklla.pellicule.ui.components.MediaCoverPlaceholder
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextSecondary
import fr.cklla.pellicule.ui.theme.TextTertiary
import fr.cklla.pellicule.ui.theme.color
import java.util.Locale

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val MOSAIC_COLUMNS = 5

/** Couleur du total de la slide d'ouverture. */
private val TotalHeroColor = TextPrimary

/** Contenu d'une slide du récap en images ; les valeurs viennent telles quelles de [RecapSlide], sans calcul. */
@Composable
internal fun RecapSlideContent(slide: RecapSlide, year: Int, onMediaClick: (String) -> Unit) {
    when (slide) {
        is RecapSlide.Total -> TotalSlide(slide)
        is RecapSlide.TypeFavorites -> TypeFavoritesSlide(slide, year, onMediaClick)
        is RecapSlide.Facts -> FactsSlide(slide, onMediaClick)
        is RecapSlide.Mosaic -> MosaicSlide(slide, year, onMediaClick)
    }
}

@Composable
private fun TotalSlide(slide: RecapSlide.Total) {
    val clock = LocalRecapEntryClock.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recap_story_total_kicker),
            style = PelliculeTextStyles.kicker,
            color = AccentPurpleLight,
            modifier = Modifier.recapEnter(clock, RecapMotion.TITLE_DELAY, RecapMotion.TITLE_DURATION),
        )
        CountUpText(
            target = slide.total,
            clock = clock,
            style = PelliculeTextStyles.recapHero,
            color = TotalHeroColor,
            modifier = Modifier.recapEnter(clock, RecapMotion.KEY_FIGURE_DELAY, RecapMotion.KEY_FIGURE_DURATION),
        )
        Text(
            text = pluralStringResource(R.plurals.recap_story_total_title, slide.total, slide.year),
            style = PelliculeTextStyles.detailTitle,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.recapEnter(clock, RecapMotion.DETAIL_DELAY, RecapMotion.DETAIL_DURATION),
        )
        Spacer(modifier = Modifier.height(32.dp))
        TypeRing(countsByType = slide.countsByType, total = slide.total, clock = clock)
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.recapEnter(clock, RecapMotion.DETAIL_DELAY, RecapMotion.DETAIL_DURATION),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MediaType.entries.forEach { type ->
                val count = slide.countsByType[type] ?: 0
                if (count > 0) TypeLegendRow(type = type, count = count, clock = clock)
            }
        }
    }
}

@Composable
private fun TypeLegendRow(type: MediaType, count: Int, clock: RecapEntryClock) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(type.color()))
        Text(
            text = stringResource(type.labelRes()),
            style = PelliculeTextStyles.legendLabel,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        CountUpText(
            target = count,
            clock = clock,
            style = PelliculeTextStyles.legendCount,
            color = type.color(),
            contentAlignment = Alignment.CenterEnd,
        )
    }
}

@Composable
private fun TypeFavoritesSlide(slide: RecapSlide.TypeFavorites, year: Int, onMediaClick: (String) -> Unit) {
    val typeColor = slide.type.color()
    val titleRes = when (slide.type) {
        MediaType.FILM -> R.string.recap_story_type_title_film
        MediaType.SERIE -> R.string.recap_story_type_title_serie
        MediaType.ANIME -> R.string.recap_story_type_title_anime
    }
    val countRes = when (slide.type) {
        MediaType.FILM -> R.plurals.recap_story_type_count_film
        MediaType.SERIE -> R.plurals.recap_story_type_count_serie
        MediaType.ANIME -> R.plurals.recap_story_type_count_anime
    }
    val clock = LocalRecapEntryClock.current
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            val titleEnter = Modifier.recapEnter(clock, RecapMotion.TITLE_DELAY, RecapMotion.TITLE_DURATION)
            Text(
                text = stringResource(R.string.recap_story_favorites_kicker),
                style = PelliculeTextStyles.kicker,
                color = AccentPurpleLight,
                modifier = titleEnter,
            )
            Text(
                text = stringResource(titleRes),
                style = PelliculeTextStyles.detailTitle,
                color = TextPrimary,
                modifier = titleEnter,
            )
            Text(
                text = pluralStringResource(countRes, slide.watchedCount, slide.watchedCount, year),
                style = PelliculeTextStyles.detailSubtitle,
                color = typeColor,
                modifier = Modifier.recapEnter(clock, RecapMotion.DETAIL_DELAY, RecapMotion.DETAIL_DURATION),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(slide.columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items = slide.favorites, key = { _, media -> media.id }) { index, media ->
                FavoriteCell(
                    media = media,
                    tint = typeColor,
                    onClick = { onMediaClick(media.id) },
                    modifier = Modifier.recapEnter(clock, RecapMotion.posterDelay(index), RecapMotion.POSTER_DURATION),
                )
            }
        }
    }
}

@Composable
private fun FavoriteCell(media: Media, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MediaCoverPlaceholder(
            title = media.title,
            posterUrl = media.posterUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO),
        )
        media.rating?.let { RatingStars(rating = it, tint = tint) }
        Text(
            text = media.title,
            style = PelliculeTextStyles.cardSubtitle,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RatingStars(rating: Int, tint: Color) {
    val description = stringResource(R.string.recap_story_rating_content_description, rating)
    Row(modifier = Modifier.semantics { contentDescription = description }) {
        for (star in 1..5) {
            val filled = star <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (filled) tint else AccentPurpleMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun FactsSlide(slide: RecapSlide.Facts, onMediaClick: (String) -> Unit) {
    val facts = slide.facts
    val clock = LocalRecapEntryClock.current
    // Les cartes présentes entrent l'une après l'autre, dans l'ordre d'affichage.
    var cardRank = 0
    fun nextCardEnter(): Modifier =
        Modifier.recapEnter(clock, RecapMotion.DETAIL_DELAY + cardRank++ * RecapMotion.DETAIL_STAGGER, RecapMotion.DETAIL_DURATION)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.recap_story_facts_title),
            style = PelliculeTextStyles.detailTitle,
            color = TextPrimary,
            modifier = Modifier.recapEnter(clock, RecapMotion.TITLE_DELAY, RecapMotion.TITLE_DURATION),
        )
        facts.first?.let {
            MediaFactCard(label = R.string.recap_story_fact_first, fact = it, onMediaClick = onMediaClick, modifier = nextCardEnter())
        }
        facts.last?.let {
            MediaFactCard(label = R.string.recap_story_fact_last, fact = it, onMediaClick = onMediaClick, modifier = nextCardEnter())
        }
        facts.busiestMonth?.let { BusiestMonthCard(it, modifier = nextCardEnter()) }
        facts.oldest?.let { OldestFactCard(media = it, onMediaClick = onMediaClick, modifier = nextCardEnter()) }
    }
}

@Composable
private fun MediaFactCard(label: Int, fact: DatedMedia, onMediaClick: (String) -> Unit, modifier: Modifier = Modifier) {
    FactCard(
        modifier = modifier,
        label = stringResource(label),
        headline = fact.media.title,
        detail = formatRecapDate(fact.date),
        media = fact.media,
        onClick = { onMediaClick(fact.media.id) },
    )
}

@Composable
private fun OldestFactCard(media: Media, onMediaClick: (String) -> Unit, modifier: Modifier = Modifier) {
    FactCard(
        modifier = modifier,
        label = stringResource(R.string.recap_story_fact_oldest),
        headline = media.title,
        detail = media.releaseYear?.let { stringResource(R.string.recap_story_fact_oldest_released, it) }.orEmpty(),
        media = media,
        onClick = { onMediaClick(media.id) },
    )
}

@Composable
private fun BusiestMonthCard(busiest: BusiestMonth, modifier: Modifier = Modifier) {
    FactCard(
        modifier = modifier,
        label = stringResource(R.string.recap_story_fact_busiest_month),
        headline = busiest.month.frenchName().replaceFirstChar { it.titlecase(Locale.FRENCH) },
        detail = pluralStringResource(R.plurals.recap_story_fact_month_count, busiest.count, busiest.count),
        media = null,
        onClick = null,
    )
}

@Composable
private fun FactCard(
    label: String,
    headline: String,
    detail: String,
    media: Media?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media != null) {
            MediaCoverPlaceholder(title = media.title, posterUrl = media.posterUrl, width = 60.dp, height = 90.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label.uppercase(Locale.FRENCH), style = PelliculeTextStyles.sectionLabel, color = TextTertiary)
            Text(
                text = headline,
                style = if (media == null) PelliculeTextStyles.donutValue else PelliculeTextStyles.cardTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(text = detail, style = PelliculeTextStyles.detailSubtitle, color = AccentPurpleLight)
            }
        }
    }
}

@Composable
private fun MosaicSlide(slide: RecapSlide.Mosaic, year: Int, onMediaClick: (String) -> Unit) {
    val clock = LocalRecapEntryClock.current
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.recap_story_mosaic_title),
                style = PelliculeTextStyles.detailTitle,
                color = TextPrimary,
                modifier = Modifier.recapEnter(clock, RecapMotion.TITLE_DELAY, RecapMotion.TITLE_DURATION),
            )
            Text(
                text = pluralStringResource(R.plurals.recap_story_mosaic_subtitle, slide.media.size, slide.media.size, year),
                style = PelliculeTextStyles.detailSubtitle,
                color = TextTertiary,
                modifier = Modifier.recapEnter(clock, RecapMotion.DETAIL_DELAY, RecapMotion.DETAIL_DURATION),
            )
        }
        // La grille entre d'un bloc : animer chaque tuile coûterait cher avec une centaine d'affiches.
        LazyVerticalGrid(
            columns = GridCells.Fixed(MOSAIC_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .recapEnter(clock, RecapMotion.DETAIL_DELAY, RecapMotion.DETAIL_DURATION),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = slide.media, key = { it.id }) { media ->
                MosaicTile(media = media, onClick = { onMediaClick(media.id) })
            }
        }
    }
}

/** Petite affiche ; sans affiche, le titre sur fond [SurfaceCard]. */
@Composable
private fun MosaicTile(media: Media, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val tileModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(POSTER_ASPECT_RATIO)
        .clip(shape)
        .clickable(onClick = onClick)
    if (media.posterUrl != null) {
        AsyncImage(
            model = media.posterUrl,
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = tileModifier,
        )
    } else {
        Box(
            modifier = tileModifier
                .background(SurfaceCard)
                .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), shape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = media.title,
                style = PelliculeTextStyles.cardSubtitle.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
