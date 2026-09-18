package fr.cklla.pellicule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.domain.model.WatchStatus
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.labelRes
import fr.cklla.pellicule.ui.theme.palette
import java.util.Locale

/** Pilule affichant le statut de visionnage d'un contenu, colorée selon [WatchStatus]. */
@Composable
fun StatusBadge(status: WatchStatus, modifier: Modifier = Modifier) {
    val palette = status.palette()
    Text(
        text = stringResource(status.labelRes()).uppercase(Locale.FRENCH),
        style = PelliculeTextStyles.badgeLabel,
        color = palette.color,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(palette.badgeBackground)
            .border(BorderStroke(0.5.dp, palette.badgeBorder), RoundedCornerShape(5.dp))
            .padding(PaddingValues(horizontal = 8.dp, vertical = 3.dp)),
    )
}
