package fr.cklla.pellicule.ui.compte

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.SuccessGreen
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextSecondary

@Composable
fun CompteScreen(
    onJellyfinSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onRecapClick: (Int) -> Unit,
    viewModel: CompteViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val connectedServerUrl by viewModel.connectedServerUrl.collectAsStateWithLifecycle()
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    CompteContent(
        signedInAs = currentUser?.displayName,
        onSignOutClick = viewModel::onSignOutClicked,
        connectedServerUrl = connectedServerUrl,
        onJellyfinSettingsClick = onJellyfinSettingsClick,
        onStatsClick = onStatsClick,
        recap = recap,
        onRecapClick = onRecapClick,
    )
}

@Composable
private fun CompteContent(
    signedInAs: String?,
    onSignOutClick: () -> Unit,
    connectedServerUrl: String?,
    onJellyfinSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    recap: RecapCardState?,
    onRecapClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = stringResource(R.string.compte_title), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
        if (signedInAs != null) {
            AccountRow(signedInAs = signedInAs, onSignOutClick = onSignOutClick)
        }
        JellyfinCard(connectedServerUrl = connectedServerUrl, onClick = onJellyfinSettingsClick)
        StatsCard(onClick = onStatsClick)
        if (recap != null) {
            RecapCard(recap = recap, onClick = { onRecapClick(recap.year) })
        }
    }
}

@Composable
private fun AccountRow(signedInAs: String, onSignOutClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.compte_signed_in_as, signedInAs),
            style = PelliculeTextStyles.cardSubtitle,
            color = TextMuted,
        )
        Text(
            text = stringResource(R.string.compte_sign_out),
            style = PelliculeTextStyles.cardSubtitle,
            color = AccentPurpleLight,
            modifier = Modifier.clickable(onClick = onSignOutClick),
        )
    }
}

@Composable
private fun JellyfinCard(connectedServerUrl: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.compte_jellyfin_action), style = PelliculeTextStyles.cardTitle, color = TextPrimary)
        Text(
            text = connectedServerUrl?.let { stringResource(R.string.compte_jellyfin_connected, it) }
                ?: stringResource(R.string.compte_jellyfin_not_connected),
            style = PelliculeTextStyles.cardSubtitle,
            color = if (connectedServerUrl != null) SuccessGreen else TextMuted,
        )
    }
}

@Composable
private fun StatsCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.compte_stats_action), style = PelliculeTextStyles.cardTitle, color = TextPrimary)
        Text(text = stringResource(R.string.compte_stats_subtitle), style = PelliculeTextStyles.cardSubtitle, color = TextMuted)
    }
}

/**
 * Carte « Récap » de l'année ciblée, affichée seulement dans la fenêtre du récap annuel. Mise en
 * avant par un fond et une bordure teintés violet pour se distinguer des cartes neutres : elle est
 * cliquable et n'apparaît que quelques semaines par an.
 */
@Composable
private fun RecapCard(recap: RecapCardState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AccentPurple.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.compte_recap_kicker), style = PelliculeTextStyles.kicker, color = AccentPurpleLight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.compte_recap_title, recap.year),
                    style = PelliculeTextStyles.statValueMedium,
                    color = TextPrimary,
                )
                Text(
                    text = pluralStringResource(R.plurals.compte_recap_subtitle, recap.watchedCount, recap.watchedCount),
                    style = PelliculeTextStyles.cardSubtitle,
                    color = TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AccentPurpleLight,
            )
        }
    }
}
