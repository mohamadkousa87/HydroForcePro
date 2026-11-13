package com.hydroforce.pro.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydroforce.pro.data.DrinkType

/** Horizontal row of chips to choose what the user is drinking. */
@Composable
fun DrinkTypePicker(
    selected: DrinkType,
    onSelected: (DrinkType) -> Unit,
    modifier: Modifier = Modifier,
    intermittentFastingActive: Boolean = false
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val lang = LocalLanguage.current
        DrinkType.entries.forEach { type ->
            val warn = intermittentFastingActive && type.breaksFast
            val label = if (warn) {
                "${type.emoji} ${type.label(lang)} " + tr("⚠️ يكسر الصيام", "⚠️ breaks fast")
            } else {
                "${type.emoji} ${type.label(lang)}"
            }
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(label) }
            )
        }
    }
}
