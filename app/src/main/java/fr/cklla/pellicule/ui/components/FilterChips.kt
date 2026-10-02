package fr.cklla.pellicule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.cklla.pellicule.R
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.ui.labelRes
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextTertiary

/**
 * Rangées de chips secondaires (année de visionnage, type de contenu), partagées entre la
 * Bibliothèque et l'écran Statistiques : même présentation, même comportement de sélection.
 */

@Composable
fun YearChipsRow(
    years: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SecondaryChip(
                label = stringResource(R.string.filter_annee_toutes),
                selected = selectedYear == null,
                onClick = { onYearSelected(null) },
            )
        }
        items(items = years, key = { it }) { year ->
            SecondaryChip(
                label = year.toString(),
                selected = year == selectedYear,
                onClick = { onYearSelected(year) },
            )
        }
    }
}

@Composable
fun SecondaryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.background(AccentPurpleMuted)
                else Modifier.border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PelliculeTextStyles.chipLabel,
            color = if (selected) TextPrimary else TextTertiary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun TypeChipsRow(
    selectedType: MediaType?,
    onTypeSelected: (MediaType?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SecondaryChip(
                label = stringResource(R.string.filter_type_tous),
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
            )
        }
        items(items = MediaType.entries, key = { it.name }) { type ->
            SecondaryChip(
                label = stringResource(type.labelRes()),
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
            )
        }
    }
}
