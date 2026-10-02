package fr.cklla.pellicule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.EpisodeKey
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.NextEpisode
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextSecondary
import java.util.Locale

/**
 * Carte d'un contenu de la Bibliothèque. [nextEpisode] et [onWatchNextEpisode] ne servent qu'aux
 * séries/anime En cours ; les autres listes (par exemple celle du récap annuel) les laissent à
 * leur valeur par défaut.
 */
@Composable
fun MediaCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nextEpisode: NextEpisode? = null,
    onWatchNextEpisode: () -> Unit = {},
) {
    Row(
        modifier = modifier
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
        Column(modifier = Modifier.weight(1f)) {
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
            if (nextEpisode != null) {
                NextEpisodeRow(media = media, nextEpisode = nextEpisode, onWatch = onWatchNextEpisode)
            }
        }
    }
}

/**
 * Prochain épisode d'une série/anime En cours : « S2 · É5 — titre » avec le "+1" quand l'épisode
 * est déjà sorti, sinon sa date de sortie. Rien à afficher pour une série terminée ou sans
 * métadonnées en cache.
 */
@Composable
private fun NextEpisodeRow(media: Media, nextEpisode: NextEpisode, onWatch: () -> Unit) {
    when (nextEpisode) {
        is NextEpisode.Available -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = episodeLabel(nextEpisode.key, nextEpisode.title),
                style = PelliculeTextStyles.cardSubtitle,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PlusOneButton(
                contentDescription = stringResource(
                    R.string.next_episode_plus_one_content_description,
                    nextEpisode.key.seasonNumber,
                    nextEpisode.key.episodeNumber,
                    media.title,
                ),
                onClick = onWatch,
            )
        }
        is NextEpisode.Upcoming -> NextEpisodeCaption(
            text = nextEpisode.airDate
                ?.let { stringResource(R.string.next_episode_upcoming_dated, it.format(Locale.FRENCH)) }
                ?: stringResource(R.string.next_episode_upcoming_undated),
        )
        NextEpisode.UpToDate -> NextEpisodeCaption(text = stringResource(R.string.next_episode_up_to_date))
        NextEpisode.Completed, NextEpisode.Unknown -> Unit
    }
}

@Composable
private fun NextEpisodeCaption(text: String) {
    Text(
        text = text,
        style = PelliculeTextStyles.cardSubtitle,
        color = TextMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun episodeLabel(key: EpisodeKey, title: String?): String =
    if (title.isNullOrBlank()) {
        stringResource(R.string.next_episode_label, key.seasonNumber, key.episodeNumber)
    } else {
        stringResource(R.string.next_episode_label_titled, key.seasonNumber, key.episodeNumber, title)
    }

@Composable
private fun PlusOneButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        // Zone de clic 48dp (accessibilité) autour de la pastille visuelle plus compacte.
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 30.dp)
                .widthIn(min = 40.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(AccentPurple),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.next_episode_plus_one),
                style = PelliculeTextStyles.chipLabel.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
        }
    }
}
