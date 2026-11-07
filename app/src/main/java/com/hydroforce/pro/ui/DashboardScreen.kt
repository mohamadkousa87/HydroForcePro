package com.hydroforce.pro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.alarm.HydrationManager
import com.hydroforce.pro.data.AlarmState
import com.hydroforce.pro.data.HydrationResult
import com.hydroforce.pro.data.IntakeLog
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.UserProfile
import com.hydroforce.pro.data.formatMl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    profile: UserProfile,
    result: HydrationResult,
    alarm: AlarmState,
    logs: List<IntakeLog>,
    now: Long,
    ringing: Boolean,
    permissions: List<PermItem>,
    onPermissionsChanged: () -> Unit,
    onDrinkNow: () -> Unit,
    onSettings: () -> Unit,
    onMeetings: () -> Unit,
    onStats: () -> Unit
) {
    val consumed = logs.sumOf { it.amountMl }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header(onSettings)
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(0.dp))
            PermissionsHealthCard(permissions, onPermissionsChanged)
            ProgressCard(consumed, result, logs.size)
            CountdownCard(profile, alarm, now, ringing, consumed >= result.targetMl)
            Button(
                onClick = onDrinkNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HydroBlue),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("شربت ماء الآن 📷", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("(توثيق بالكاميرا وإعادة ضبط المؤقت)", fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onStats,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("📊 الإحصائيات", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onMeetings,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("📅 الاجتماعات", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            UrineColorGuide()
            TodayLogCard(logs)
            ResultCard(result, profile.cupSizeMl, title = "📋 لماذا هذا الهدف الطبي؟")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HydroDeep, HydroBlue)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("هايدرو فورس برو 💧", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("ترطيب طبي ملزم بالتوثيق", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            TextButton(onClick = onSettings) {
                Text("⚙️ الإعدادات", color = Color.White)
            }
        }
    }
}

@Composable
private fun ProgressCard(consumed: Int, result: HydrationResult, cups: Int) {
    val fraction = (consumed.toFloat() / result.targetMl).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900), label = "progress")
    val percent = (consumed * 100f / result.targetMl).toInt()
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Color(0xFFE3EEF8),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(HydroSky, HydroBlue, HydroSky)),
                        startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = HydroDeep)
                    Text("💧", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("تقدم اليوم", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
                Text(
                    "${formatMl(consumed)} / ${formatMl(result.targetMl)} مل",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = HydroBlue
                )
                Text("مل مستهلك / الهدف الطبي اليومي", fontSize = 12.sp, color = Color.Gray)
                Text("🥤 الأكواب: $cups من ${result.cupsNeeded}", fontWeight = FontWeight.SemiBold)
                if (consumed >= result.targetMl) {
                    Text("🎉 حققت هدفك الطبي اليوم!", color = HydroMint, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        "متبقٍ ${formatMl(result.targetMl - consumed)} مل",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownCard(profile: UserProfile, alarm: AlarmState, now: Long, ringing: Boolean, goalMet: Boolean) {
    val awake = ScheduleMath.isAwake(now, profile)
    val work = ScheduleMath.isWorkTime(now, profile)
    val badge = when {
        !awake -> "وقت النوم 😴" to Color(0xFF5B5F97)
        work -> "وضع العمل مفعل 💼" to Color(0xFF1F7A5C)
        else -> "الوضع العادي 🔔" to HydroWarn
    }
    val remainingMs = alarm.nextAlarmAt - now
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = HydroDeep),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏱️ العد التنازلي", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    badge.first,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badge.second)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            when {
                ringing -> Text("🔔 التنبيه يرن الآن — وثّق شرب الماء!", color = HydroSky, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                alarm.nextAlarmAt <= 0L || remainingMs <= 0 ->
                    Text("جارٍ جدولة التنبيه القادم...", color = Color.White)
                else -> {
                    val totalSec = remainingMs / 1000
                    val h = totalSec / 3600
                    val m = (totalSec % 3600) / 60
                    val s = totalSec % 60
                    Text(
                        String.format(Locale.US, "التنبيه القادم بعد: %02d ساعة و %02d دقيقة", h, m),
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        String.format(Locale.US, "%02d:%02d:%02d", h, m, s),
                        color = HydroSky,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    val at = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date(alarm.nextAlarmAt))
                    val mode = if (ScheduleMath.isWorkTime(alarm.nextAlarmAt, profile)) "هادئ 💼" else "كامل 🔔"
                    Text("الموعد: $at • نوع التنبيه: $mode", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    if (goalMet) {
                        Text("✅ الهدف مكتمل — التنبيه التالي عند الاستيقاظ", color = HydroSky, fontSize = 13.sp)
                    }
                    if (alarm.snoozeUsed > 0) {
                        Text("⏰ غفوات مستخدمة في هذه الدورة: ${alarm.snoozeUsed}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private data class UrineShade(val color: Color, val level: String, val advice: String)

private val urineShades = listOf(
    UrineShade(Color(0xFFF8F9E8), "1 — ترطيب ممتاز", "لون شفاف تقريباً. أنت رطب جيداً، حافظ على الوتيرة ولا تفرط."),
    UrineShade(Color(0xFFF6F3C2), "2 — ترطيب ممتاز", "أصفر باهت جداً. هذا هو اللون المثالي."),
    UrineShade(Color(0xFFF3EB9A), "3 — ترطيب جيد", "أصفر فاتح. ترطيب صحي، استمر بالالتزام بالتنبيهات."),
    UrineShade(Color(0xFFEFDE6E), "4 — مقبول", "أصفر. ابدأ بالانتباه واشرب كأساً قريباً."),
    UrineShade(Color(0xFFE6C84B), "5 — جفاف خفيف", "أصفر داكن. اشرب كأساً الآن ولا تؤجل التنبيه القادم."),
    UrineShade(Color(0xFFD6AA34), "6 — جفاف", "كهرماني. جسمك يحتاج ماءً، اشرب 1–2 كأس خلال الساعة."),
    UrineShade(Color(0xFFB98A22), "7 — جفاف واضح", "برتقالي داكن. اشرب فوراً وراجع التزامك بالجدول."),
    UrineShade(Color(0xFF8E6516), "8 — جفاف شديد", "بني/كهرماني غامق. اشرب الماء فوراً واستشر طبيباً إن استمر اللون أو صاحبه دوار.")
)

@Composable
private fun UrineColorGuide() {
    var selected by remember { mutableIntStateOf(-1) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🧪 دليل ألوان البول للترطيب", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
            Text("اضغط على اللون الأقرب لحالتك لمعرفة مستوى ترطيبك", fontSize = 13.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                urineShades.forEachIndexed { i, shade ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (selected == i) 58.dp else 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(shade.color)
                            .border(
                                if (selected == i) 3.dp else 1.dp,
                                if (selected == i) HydroDeep else Color(0x22000000),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selected = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = if (i < 5) Color(0xFF6B5B00) else Color.White)
                    }
                }
            }
            Row {
                Text("رطب جيداً 💧", fontSize = 12.sp, color = HydroMint, modifier = Modifier.weight(1f))
                Text("جفاف ⚠️", fontSize = 12.sp, color = HydroDanger)
            }
            AnimatedVisibility(visible = selected >= 0) {
                val shade = urineShades.getOrNull(selected)
                if (shade != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(shade.color.copy(alpha = 0.35f))
                            .padding(12.dp)
                    ) {
                        Text(shade.level, fontWeight = FontWeight.Bold)
                        Text(shade.advice, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayLogCard(logs: List<IntakeLog>) {
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("📒 سجل اليوم (موثق بالكاميرا)", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
            Spacer(Modifier.height(6.dp))
            if (logs.isEmpty()) {
                Text("لم يتم تسجيل أي كأس بعد اليوم.", color = Color.Gray)
            } else {
                logs.forEachIndexed { i, log ->
                    if (i > 0) HorizontalDivider(color = Color(0xFFEFF3F7))
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (log.source == HydrationManager.SOURCE_ALARM) "🔔" else "📷", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("+${log.amountMl} مل", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (log.source == HydrationManager.SOURCE_ALARM) "عند التنبيه" else "توثيق استباقي",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Text(fmt.format(Date(log.timestamp)), color = Color.Gray)
                    }
                }
            }
        }
    }
}
