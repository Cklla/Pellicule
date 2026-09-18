package fr.cklla.pellicule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.R
import fr.cklla.pellicule.ui.AppTab
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.TextMuted

private data class TabSpec(val tab: AppTab, val icon: ImageVector, val labelRes: Int)

private val tabs = listOf(
    TabSpec(AppTab.BIBLIOTHEQUE, Icons.AutoMirrored.Outlined.MenuBook, R.string.bibliotheque_title),
    TabSpec(AppTab.RECHERCHE, Icons.Outlined.Search, R.string.nav_recherche),
    TabSpec(AppTab.STATS, Icons.Outlined.BarChart, R.string.nav_stats),
)

/** Barre de navigation basse fixe à 3 onglets, présente sur tous les écrans de premier niveau. */
@Composable
fun BottomNavBar(selectedTab: AppTab, onTabSelected: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark),
    ) {
        HorizontalDivider(color = BorderHairline.copy(alpha = 0.5f), thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { spec ->
                val selected = spec.tab == selectedTab
                val tint = if (selected) AccentPurpleLight else TextMuted
                Column(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { onTabSelected(spec.tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = spec.icon, contentDescription = null, tint = tint)
                    Text(text = stringResource(spec.labelRes), style = PelliculeTextStyles.navLabel, color = tint)
                }
            }
        }
    }
}
