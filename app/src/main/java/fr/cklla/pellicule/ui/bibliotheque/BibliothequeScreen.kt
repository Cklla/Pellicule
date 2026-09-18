package fr.cklla.pellicule.ui.bibliotheque

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.cklla.pellicule.R
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary

/**
 * Écran Bibliothèque : liste des contenus suivis. Squelette pour l'instant (état vide uniquement)
 * — la liste, les filtres par statut et l'ajout viendront dans une prochaine session (voir
 * WORK.md), sur le modèle de l'écran équivalent de Cartouche.
 */
@Composable
fun BibliothequeScreen(viewModel: BibliothequeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.bibliotheque_title), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
        if (uiState.media.isEmpty()) {
            Text(text = stringResource(R.string.empty_title), style = PelliculeTextStyles.emptyTitle, color = TextPrimary)
            Text(text = stringResource(R.string.empty_message_bibliotheque), style = PelliculeTextStyles.emptyMessage, color = TextMuted)
        }
    }
}
