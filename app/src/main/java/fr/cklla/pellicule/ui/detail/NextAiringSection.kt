package fr.cklla.pellicule.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.calendar.MonthGrid
import fr.cklla.pellicule.domain.calendar.NextAiring
import fr.cklla.pellicule.domain.calendar.buildMonthGrid
import fr.cklla.pellicule.ui.permission.rememberNotificationPermissionRequester
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.ErrorCoral
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextTertiary
import java.util.Locale

/**
 * Prochaine diffusion d'une série ou d'un anime en cours : mini-calendrier du mois de la diffusion,
 * ligne de texte, et interrupteur du rappel (uniquement pour un contenu déjà suivi, [canRemind]).
 *
 * Aucune heure n'est annoncée : TMDB ne donne qu'une date, dans le fuseau du pays d'origine, qui
 * peut tomber un jour plus tôt ou plus tard en France (voir `detail_next_airing_disclaimer`).
 */
@Composable
internal fun NextAiringSection(
    airing: NextAiring,
    reminderEnabled: Boolean,
    canRemind: Boolean,
    onReminderToggled: (Boolean) -> Unit,
) {
    Column {
        SectionLabel(stringResource(R.string.detail_next_airing_label))
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthCalendar(
                grid = buildMonthGrid(airing.airDate.year, airing.airDate.month),
                monthLabel = airing.airDate.formatMonthYear(Locale.FRENCH)
                    .replaceFirstChar { it.titlecase(Locale.FRENCH) },
                highlightedDay = airing.airDate.day,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = airingLine(airing),
                    style = PelliculeTextStyles.cardTitle,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.detail_next_airing_disclaimer),
                    style = PelliculeTextStyles.cardSubtitle,
                    color = TextMuted,
                )
            }
        }
        if (canRemind) {
            Spacer(modifier = Modifier.height(8.dp))
            ReminderToggle(enabled = reminderEnabled, onToggled = onReminderToggled)
        }
    }
}

@Composable
private fun airingLine(airing: NextAiring): String {
    val date = airing.airDate.formatLong(Locale.FRENCH)
    return if (airing.key.seasonNumber > 1) {
        stringResource(R.string.detail_next_airing_line_season, airing.key.seasonNumber, airing.key.episodeNumber, date)
    } else {
        stringResource(R.string.detail_next_airing_line, airing.key.episodeNumber, date)
    }
}

@Composable
private fun MonthCalendar(grid: MonthGrid, monthLabel: String, highlightedDay: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = monthLabel, style = PelliculeTextStyles.cardTitle, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // Semaine commençant le lundi, comme la grille.
            stringResource(R.string.detail_calendar_weekday_initials).forEach { initial ->
                Text(
                    text = initial.toString(),
                    style = PelliculeTextStyles.sectionLabel,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        grid.weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(day = day, highlighted = day == highlightedDay, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int?, highlighted: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(36.dp), contentAlignment = Alignment.Center) {
        if (day != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .then(if (highlighted) Modifier.clip(CircleShape).background(AccentPurple) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.toString(),
                    style = PelliculeTextStyles.chipLabel,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (highlighted) TextPrimary else TextTertiary,
                )
            }
        }
    }
}

/**
 * Interrupteur « Me prévenir à la sortie de chaque épisode ». La permission de notification n'est
 * demandée qu'au moment de l'activer ; si elle est refusée, l'interrupteur reste éteint et un
 * court message le dit — le calendrier, lui, n'en dépend pas.
 */
@Composable
private fun ReminderToggle(enabled: Boolean, onToggled: (Boolean) -> Unit) {
    val permissionRequester = rememberNotificationPermissionRequester()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val onCheckedChange: (Boolean) -> Unit = { wanted ->
        if (!wanted) {
            permissionDenied = false
            onToggled(false)
        } else {
            permissionRequester.request { granted ->
                permissionDenied = !granted
                if (granted) onToggled(true)
            }
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onCheckedChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                contentDescription = null,
                tint = if (enabled) AccentPurple else TextMuted,
            )
            Text(
                text = stringResource(R.string.detail_reminder_label),
                style = PelliculeTextStyles.cardTitle,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // Le clic est porté par la ligne entière (`toggleable`) : l'interrupteur n'est qu'un témoin.
            Switch(
                checked = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = AccentPurple,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = BackgroundDark,
                    uncheckedBorderColor = AccentPurpleMuted,
                ),
            )
        }
        if (permissionDenied) {
            Text(
                text = stringResource(R.string.detail_reminder_denied),
                style = PelliculeTextStyles.emptyMessage,
                color = ErrorCoral,
            )
        }
    }
}
