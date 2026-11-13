package com.hydroforce.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.data.HydrationTip
import com.hydroforce.pro.data.HydrationTips
import com.hydroforce.pro.data.TipCategory

private fun accentFor(category: TipCategory): Color = when (category) {
    TipCategory.BENEFITS -> HydroBlue
    TipCategory.DEHYDRATION -> HydroDanger
    TipCategory.SUMMER -> HydroWarn
    TipCategory.SPORT -> HydroMint
    TipCategory.FASTING -> Color(0xFF6A5ACD)
    TipCategory.HABITS -> Color(0xFF1F8A70)
}

@Composable
fun HydrationTipsScreen(onBack: (() -> Unit)? = null) {
    val lang = LocalLanguage.current
    var selected by remember { mutableStateOf<TipCategory?>(null) }
    val tipOfDay = remember { HydrationTips.tipOfTheDay() }
    val tips = remember(selected) {
        if (selected == null) HydrationTips.all else HydrationTips.byCategory(selected!!)
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HydroDeep, HydroBlue)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text(tr("→ رجوع", "→ Back"), color = Color.White) }
                }
                Column(Modifier.weight(1f)) {
                    Text(tr("💡 صحة الترطيب", "💡 Hydration health"), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(tr("نصائح وحقائق لجسم أكثر ترطيباً", "Tips and facts for a better-hydrated body"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            item { TipOfDayCard(tipOfDay) }

            item {
                val scroll = rememberScrollState()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selected == null,
                        onClick = { selected = null },
                        label = { Text(tr("الكل", "All")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HydroBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                    TipCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selected == cat,
                            onClick = { selected = cat },
                            label = { Text("${cat.emoji} ${cat.label(lang)}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentFor(cat),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            items(tips) { tip -> TipCard(tip) }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun TipOfDayCard(tip: HydrationTip) {
    val lang = LocalLanguage.current
    val accent = accentFor(tip.category)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HydroDeep),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("✨ نصيحة اليوم", "✨ Tip of the day"), color = HydroSky, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(tip.emoji, accent, size = 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tip.title(lang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(tip.category.label(lang), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            Text(tip.body(lang), color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp)
        }
    }
}

@Composable
private fun TipCard(tip: HydrationTip) {
    val lang = LocalLanguage.current
    val accent = accentFor(tip.category)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            EmojiBadge(tip.emoji, accent, size = 48)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tip.title(lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(
                    "${tip.category.emoji} ${tip.category.label(lang)}",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(tip.body(lang), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmojiBadge(emoji: String, accent: Color, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size * 0.45).sp)
    }
}
