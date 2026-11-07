package com.hydroforce.pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.data.StatsData
import com.hydroforce.pro.data.formatMl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    stats: StatsData?,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("→ رجوع") }
            Text("📊 الإحصائيات والتاريخ", style = MaterialTheme.typography.headlineMedium, color = HydroDeep)
        }

        if (stats == null) {
            Text("جارٍ حساب الإحصائيات...", color = Color.Gray)
            return@Column
        }

        // Streak highlight
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = HydroDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 40.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("${stats.streak} يوم", color = HydroSky, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("سلسلة الأيام التي حقّقت فيها هدفك المتتالية", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Stat tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("المتوسط اليومي", "${formatMl(stats.avgPerDayMl)} مل", Modifier.weight(1f))
            StatTile("أيام الهدف", "${stats.daysGoalMet} / ${stats.days.size}", Modifier.weight(1f))
        }

        // Weekly chart
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("آخر ${stats.days.size} أيام", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
                Text("الخط المتقطّع = هدفك اليومي (${formatMl(stats.targetMl)} مل)", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                WeeklyBarChart(stats)
            }
        }

        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("📤 تصدير السجل (CSV)")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HydroBlue)
            Text(label, fontSize = 12.sp, color = HydroDeep)
        }
    }
}

@Composable
private fun WeeklyBarChart(stats: StatsData) {
    val maxValue = remember(stats) {
        (stats.days.maxOfOrNull { it.totalMl } ?: 0).coerceAtLeast(stats.targetMl).coerceAtLeast(1)
    }
    val scale = maxValue * 1.15f
    val barColorMet = HydroMint
    val barColorMiss = HydroSky
    val targetColor = HydroWarn
    val trackColor = Color(0xFFE3EEF8)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val n = stats.days.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val barWidth = slot * 0.55f
        val targetY = size.height - (stats.targetMl / scale) * size.height

        stats.days.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2
            val left = cx - barWidth / 2
            // track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3)
            )
            val barHeight = (day.totalMl / scale) * size.height
            drawRoundRect(
                color = if (day.goalMet) barColorMet else barColorMiss,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3)
            )
        }
        // target line
        drawLine(
            color = targetColor,
            start = Offset(0f, targetY),
            end = Offset(size.width, targetY),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    }

    Spacer(Modifier.height(6.dp))
    val dayFmt = remember { SimpleDateFormat("EEE", Locale("ar")) }
    Row(Modifier.fillMaxWidth()) {
        stats.days.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayFmt.format(Date(day.dayStartMillis)), fontSize = 11.sp, color = Color.Gray)
                Text(
                    String.format(Locale.US, "%.1fل", day.totalMl / 1000f),
                    fontSize = 10.sp, color = HydroDeep, textAlign = TextAlign.Center
                )
            }
        }
    }
}
