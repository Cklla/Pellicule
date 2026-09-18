package fr.cklla.pellicule.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.SuccessGreen
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary

/** Écran Stats : vue d'ensemble de la progression (à construire) + accès à la connexion Jellyfin, à cet endroit plutôt que dans un écran Réglages dédié (une seule action de ce type pour l'instant). */
@Composable
fun StatsScreen(onJellyfinSettingsClick: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val connectedServerUrl by viewModel.connectedServerUrl.collectAsStateWithLifecycle()
    StatsContent(connectedServerUrl = connectedServerUrl, onJellyfinSettingsClick = onJellyfinSettingsClick)
}

@Composable
private fun StatsContent(connectedServerUrl: String?, onJellyfinSettingsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(text = stringResource(R.string.nav_stats), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
            Text(text = stringResource(R.string.stats_placeholder), style = PelliculeTextStyles.emptyMessage, color = TextMuted)
        }
        JellyfinCard(connectedServerUrl = connectedServerUrl, onClick = onJellyfinSettingsClick)
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
        Text(text = stringResource(R.string.stats_jellyfin_action), style = PelliculeTextStyles.cardTitle, color = TextPrimary)
        Text(
            text = connectedServerUrl?.let { stringResource(R.string.stats_jellyfin_connected, it) }
                ?: stringResource(R.string.stats_jellyfin_not_connected),
            style = PelliculeTextStyles.cardSubtitle,
            color = if (connectedServerUrl != null) SuccessGreen else TextMuted,
        )
    }
}
