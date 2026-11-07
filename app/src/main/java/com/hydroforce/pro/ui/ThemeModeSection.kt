package com.hydroforce.pro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.ThemeMode
import kotlinx.coroutines.launch

/** Appearance (light/dark/system) and language (Arabic/English) selectors for settings. */
@Composable
fun ThemeModeSection() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val mode by repo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val language by repo.language.collectAsStateWithLifecycle(initialValue = Language.AR)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("🎨 مظهر التطبيق", "🎨 Appearance"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { scope.launch { repo.setThemeMode(m) } },
                            label = { Text(m.label(language)) }
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("🌐 اللغة", "🌐 Language"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Language.entries.forEach { l ->
                        FilterChip(
                            selected = language == l,
                            onClick = { scope.launch { repo.setLanguage(l) } },
                            label = { Text(l.label) }
                        )
                    }
                }
            }
        }
    }
}
