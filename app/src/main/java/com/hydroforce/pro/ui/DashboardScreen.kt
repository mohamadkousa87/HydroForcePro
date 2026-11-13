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
import androidx.compose.material3.FilterChip
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
import com.hydroforce.pro.data.DrinkType
import com.hydroforce.pro.data.FastingMode
import com.hydroforce.pro.data.HydrationResult
import com.hydroforce.pro.data.IntakeLog
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.UserProfile
import com.hydroforce.pro.data.VolumeUnit
import com.hydroforce.pro.data.formatMl
import com.hydroforce.pro.data.formatVolume
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun clockLocale(lang: Language): Locale = if (lang == Language.EN) Locale.ENGLISH else Locale("ar")

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
    selectedDrinkType: DrinkType,
    onDrinkTypeSelected: (DrinkType) -> Unit,
    workoutActive: Boolean,
    onStartWorkout: (Int) -> Unit,
    onEndWorkout: () -> Unit,
    todayBioCount: Int,
    onBioGrade: (Int) -> Unit,
    onUndoBio: () -> Unit
) {
    val lang = LocalLanguage.current
    val consumed = logs.sumOf { it.effectiveMl }
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(tr("حسناً", "OK")) } },
            title = { Text(tr("تفصيل الهدف الطبي", "Medical goal breakdown")) },
            text = { ResultCard(result, profile.cupSizeMl, profile.volumeUnit, title = tr("📋 لماذا هذا الهدف؟", "📋 Why this goal?")) }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header(profile, now, workoutActive)
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(0.dp))
            PermissionsHealthCard(permissions, onPermissionsChanged)
            ProgressCard(consumed, result, logs.size, profile.volumeUnit) { showInfo = true }
            CountdownCard(profile, alarm, now, ringing, consumed >= result.targetMl)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("🥤 ماذا ستشرب؟", "🥤 What are you drinking?"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    val imFasting = profile.fastingMode == FastingMode.INTERMITTENT_FAST &&
                        ScheduleMath.isFastingDay(now, profile) && ScheduleMath.inFastWindow(now, profile)
                    DrinkTypePicker(
                        selected = selectedDrinkType,
                        onSelected = onDrinkTypeSelected,
                        intermittentFastingActive = imFasting
                    )
                    if (selectedDrinkType.hydrationFactor < 1f) {
                        val pct = (selectedDrinkType.hydrationFactor * 100).toInt()
                        Text(
                            tr("ملاحظة: يُحتسب $pct% من الكمية كترطيب لهذا النوع.", "Note: $pct% of this drink counts as hydration."),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
                    Text(
                        tr("شربت ${selectedDrinkType.label(lang)} الآن 📷", "I drank ${selectedDrinkType.label(lang)} now 📷"),
                        fontSize = 21.sp, fontWeight = FontWeight.Bold
                    )
                    Text(tr("(توثيق بالكاميرا وإعادة ضبط المؤقت)", "(camera-verified, resets the timer)"), fontSize = 13.sp)
                }
            }
            WorkoutCard(active = workoutActive, onStart = onStartWorkout, onEnd = onEndWorkout)
            UrineColorGuide(todayBioCount = todayBioCount, onGrade = onBioGrade, onUndo = onUndoBio)
            TodayLogCard(logs)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(profile: UserProfile, now: Long, workoutActive: Boolean) {
    val (badge, badgeColor) = when {
        workoutActive -> tr("تمرين 🏋️", "Workout 🏋️") to HydroMint
        profile.fastingMode == FastingMode.DRY_FAST && ScheduleMath.isFastingDay(now, profile) ->
            tr("صيام جاف 🌙", "Dry fast 🌙") to Color(0xFF6A5ACD)
        profile.fastingMode == FastingMode.INTERMITTENT_FAST && ScheduleMath.isFastingDay(now, profile) ->
            tr("صيام متقطع ⏳", "Intermittent ⏳") to Color(0xFF6A5ACD)
        ScheduleMath.isWorkTime(now, profile) -> tr("وضع العمل 💼", "Work mode 💼") to Color(0xFF1F7A5C)
        else -> tr("الوضع العادي 🔔", "Normal 🔔") to HydroWarn
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HydroDeep, HydroBlue)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VeroSip 💧", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(tr("ترطيب طبي ملزم بالتوثيق", "Verified medical hydration"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            Text(
                badge,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProgressCard(consumed: Int, result: HydrationResult, cups: Int, unit: VolumeUnit, onInfo: () -> Unit) {
    val lang = LocalLanguage.current
    val fraction = (consumed.toFloat() / result.targetMl).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900), label = "progress")
    val percent = (consumed * 100f / result.targetMl).toInt()
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = trackColor,
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
                    Text("$percent%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("💧", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("تقدم اليوم", "Today's progress"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = onInfo, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { Text("ℹ️", fontSize = 18.sp) }
                }
                Text(
                    "${formatVolume(consumed, unit, lang)} / ${formatVolume(result.targetMl, unit, lang)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = HydroBlue
                )
                Text(tr("مستهلك / الهدف الطبي اليومي", "consumed / daily medical goal"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("🥤 الأكواب: $cups من ${result.cupsNeeded}", "🥤 Cups: $cups of ${result.cupsNeeded}"), fontWeight = FontWeight.SemiBold)
                if (consumed >= result.targetMl) {
                    Text(tr("🎉 حققت هدفك الطبي اليوم!", "🎉 You met today's goal!"), color = HydroMint, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        tr("متبقٍ ${formatVolume(result.targetMl - consumed, unit, lang)}", "${formatVolume(result.targetMl - consumed, unit, lang)} remaining"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownCard(profile: UserProfile, alarm: AlarmState, now: Long, ringing: Boolean, goalMet: Boolean) {
    val lang = LocalLanguage.current
    val awake = ScheduleMath.isAwake(now, profile)
    val work = ScheduleMath.isWorkTime(now, profile)
    val badge = when {
        !awake -> tr("وقت النوم 😴", "Sleep time 😴") to Color(0xFF5B5F97)
        work -> tr("وضع العمل مفعل 💼", "Work mode on 💼") to Color(0xFF1F7A5C)
        else -> tr("الوضع العادي 🔔", "Normal mode 🔔") to HydroWarn
    }
    val remainingMs = alarm.nextAlarmAt - now
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = HydroDeep),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("⏱️ العد التنازلي", "⏱️ Countdown"), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                ringing -> Text(tr("🔔 التنبيه يرن الآن — وثّق شرب الماء!", "🔔 Alarm ringing — verify drinking!"), color = HydroSky, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                alarm.nextAlarmAt <= 0L || remainingMs <= 0 ->
                    Text(tr("جارٍ جدولة التنبيه القادم...", "Scheduling next reminder..."), color = Color.White)
                else -> {
                    val totalSec = remainingMs / 1000
                    val h = totalSec / 3600
                    val m = (totalSec % 3600) / 60
                    val s = totalSec % 60
                    Text(
                        tr(
                            String.format(Locale.US, "التنبيه القادم بعد: %02d ساعة و %02d دقيقة", h, m),
                            String.format(Locale.US, "Next reminder in: %02d h %02d m", h, m)
                        ),
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
                    val at = SimpleDateFormat("hh:mm a", clockLocale(lang)).format(Date(alarm.nextAlarmAt))
                    val mode = if (ScheduleMath.isWorkTime(alarm.nextAlarmAt, profile)) tr("هادئ 💼", "Quiet 💼") else tr("كامل 🔔", "Full 🔔")
                    Text(tr("الموعد: $at • نوع التنبيه: $mode", "At: $at • Type: $mode"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    if (goalMet) {
                        Text(tr("✅ الهدف مكتمل — التنبيه التالي عند الاستيقاظ", "✅ Goal met — next reminder at wake-up"), color = HydroSky, fontSize = 13.sp)
                    }
                    if (alarm.snoozeUsed > 0) {
                        Text(tr("⏰ غفوات مستخدمة في هذه الدورة: ${alarm.snoozeUsed}", "⏰ Snoozes used this cycle: ${alarm.snoozeUsed}"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private data class UrineShade(
    val color: Color,
    val level: String, val levelEn: String,
    val advice: String, val adviceEn: String
)

private val urineShades = listOf(
    UrineShade(Color(0xFFF8F9E8), "1 — ترطيب ممتاز", "1 — Excellent", "لون شفاف تقريباً. أنت رطب جيداً، حافظ على الوتيرة ولا تفرط.", "Almost clear. Well hydrated — keep the pace, don't overdo it."),
    UrineShade(Color(0xFFF6F3C2), "2 — ترطيب ممتاز", "2 — Excellent", "أصفر باهت جداً. هذا هو اللون المثالي.", "Very pale yellow. This is the ideal color."),
    UrineShade(Color(0xFFF3EB9A), "3 — ترطيب جيد", "3 — Good", "أصفر فاتح. ترطيب صحي، استمر بالالتزام بالتنبيهات.", "Light yellow. Healthy hydration, keep following reminders."),
    UrineShade(Color(0xFFEFDE6E), "4 — مقبول", "4 — Fair", "أصفر. ابدأ بالانتباه واشرب كأساً قريباً.", "Yellow. Pay attention and drink a cup soon."),
    UrineShade(Color(0xFFE6C84B), "5 — جفاف خفيف", "5 — Mild dehydration", "أصفر داكن. اشرب كأساً الآن ولا تؤجل التنبيه القادم.", "Dark yellow. Drink a cup now and don't postpone the next reminder."),
    UrineShade(Color(0xFFD6AA34), "6 — جفاف", "6 — Dehydrated", "كهرماني. جسمك يحتاج ماءً، اشرب 1–2 كأس خلال الساعة.", "Amber. Your body needs water, drink 1–2 cups within the hour."),
    UrineShade(Color(0xFFB98A22), "7 — جفاف واضح", "7 — Clear dehydration", "برتقالي داكن. اشرب فوراً وراجع التزامك بالجدول.", "Dark orange. Drink immediately and review your schedule."),
    UrineShade(Color(0xFF8E6516), "8 — جفاف شديد", "8 — Severe dehydration", "بني/كهرماني غامق. اشرب الماء فوراً واستشر طبيباً إن استمر اللون أو صاحبه دوار.", "Dark brown/amber. Drink water now and see a doctor if it persists or you feel dizzy.")
)

@Composable
private fun WorkoutCard(active: Boolean, onStart: (Int) -> Unit, onEnd: () -> Unit) {
    var duration by remember { mutableIntStateOf(60) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("🏋️ وضع التمرين", "🏋️ Workout mode"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (active) {
                Text(
                    tr("التمرين جارٍ — نبضات رشفة كل 15 دقيقة على ساعتك، و+500 مل تعويض تعرق.", "Workout in progress — sip pulses every 15 min on your watch, +500 ml sweat bonus."),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onEnd,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HydroDanger)
                ) { Text(tr("⏹️ إنهاء التمرين مبكراً", "⏹️ End workout early")) }
            } else {
                Text(
                    tr("اختر المدة وابدأ — نذكّرك برشفات أثناء التمرين ونضيف 500 مل تعويض تعرق.", "Pick a duration and start — we'll pulse sips and add a 500 ml sweat bonus."),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(45, 60, 90).forEach { d ->
                        FilterChip(
                            selected = duration == d,
                            onClick = { duration = d },
                            label = { Text(tr("$d دقيقة", "$d min")) }
                        )
                    }
                }
                Button(
                    onClick = { onStart(duration) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HydroBlue)
                ) { Text(tr("ابدأ التمرين 🏋️", "Start workout 🏋️"), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun UrineColorGuide(todayBioCount: Int, onGrade: (Int) -> Unit, onUndo: () -> Unit) {
    val lang = LocalLanguage.current
    var selected by remember { mutableIntStateOf(-1) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🧪 المؤشر الحيوي للون البول", "🧪 Urine color bio-check"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(tr("🚽 مرات اليوم: $todayBioCount", "🚽 Today: $todayBioCount"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HydroBlue)
            }
            Text(tr("اضغط الدرجة الأقرب لحالتك — نضيف ماء إنقاذ تلقائياً عند الحاجة", "Tap the closest grade — rescue water is added automatically when needed"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            .clickable {
                                selected = i
                                onGrade(i + 1)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = if (i < 5) Color(0xFF6B5B00) else Color.White)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("رطب جيداً 💧", "Well hydrated 💧"), fontSize = 12.sp, color = HydroMint, modifier = Modifier.weight(1f))
                if (todayBioCount > 0) {
                    TextButton(onClick = { onUndo(); selected = -1 }) { Text(tr("↩️ تراجع", "↩️ Undo")) }
                }
                Text(tr("جفاف ⚠️", "Dehydration ⚠️"), fontSize = 12.sp, color = HydroDanger)
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
                        Text(if (lang == Language.EN) shade.levelEn else shade.level, fontWeight = FontWeight.Bold)
                        Text(if (lang == Language.EN) shade.adviceEn else shade.advice, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayLogCard(logs: List<IntakeLog>) {
    val lang = LocalLanguage.current
    val ml = tr("مل", "ml")
    val fmt = remember(lang) { SimpleDateFormat("hh:mm a", clockLocale(lang)) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("📒 سجل اليوم (موثق بالكاميرا)", "📒 Today's log (camera-verified)"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            if (logs.isEmpty()) {
                Text(tr("لم يتم تسجيل أي كأس بعد اليوم.", "No drinks logged yet today."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                logs.forEachIndexed { i, log ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    val type = DrinkType.fromName(log.drinkType)
                    val via = if (log.source == HydrationManager.SOURCE_ALARM) tr("🔔 عند التنبيه", "🔔 At reminder") else tr("📷 توثيق استباقي", "📷 Proactive")
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(type.emoji, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${type.label(lang)} +${log.amountMl} $ml", fontWeight = FontWeight.SemiBold)
                            Text(via, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmt.format(Date(log.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
