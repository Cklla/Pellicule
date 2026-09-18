package fr.cklla.pellicule.ui.recherche

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.R
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary

/** Écran Recherche : branchement TMDB à venir (voir WORK.md). Placeholder pour l'instant. */
@Composable
fun RechercheScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.nav_recherche), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
        Text(text = stringResource(R.string.recherche_placeholder), style = PelliculeTextStyles.emptyMessage, color = TextMuted)
    }
}
