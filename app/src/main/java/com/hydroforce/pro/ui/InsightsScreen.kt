package com.hydroforce.pro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hydroforce.pro.data.StatsData

/** The "Insights" tab: a switch between Stats and Medical tips. */
@Composable
fun InsightsScreen(stats: StatsData?, onExport: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("الإحصائيات", "Stats")) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("النصائح الطبية", "Medical tips")) })
        }
        when (tab) {
            0 -> StatsScreen(stats = stats, onExport = onExport, onBack = null, embedded = true)
            else -> HydrationTipsScreen(onBack = null)
        }
    }
}
