package com.hydroforce.pro.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.data.FastingMode
import com.hydroforce.pro.data.FastingRecurrence
import com.hydroforce.pro.data.HydrationCalculator
import com.hydroforce.pro.data.Meeting
import com.hydroforce.pro.data.UserProfile
import com.hydroforce.pro.data.formatClock
import com.hydroforce.pro.data.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The "Schedule" tab: all time-related settings (fasting, wake/sleep/work, meals) + meetings. */
@Composable
fun ScheduleScreen(
    initial: UserProfile,
    meetings: List<Meeting>,
    onSave: (UserProfile) -> Unit,
    onRemoveMeeting: (Long) -> Unit,
    onOpenMeetings: () -> Unit
) {
    val lang = LocalLanguage.current
    var wake by rememberSaveable { mutableIntStateOf(initial.wakeMinutes) }
    var sleep by rememberSaveable { mutableIntStateOf(initial.sleepMinutes) }
    var workEnabled by rememberSaveable { mutableStateOf(initial.workModeEnabled) }
    var workStart by rememberSaveable { mutableIntStateOf(initial.workStartMinutes) }
    var workEnd by rememberSaveable { mutableIntStateOf(initial.workEndMinutes) }
    var mealsEnabled by rememberSaveable { mutableStateOf(initial.mealsEnabled) }
    var breakfast by rememberSaveable { mutableIntStateOf(initial.breakfastMinutes) }
    var lunch by rememberSaveable { mutableIntStateOf(initial.lunchMinutes) }
    var dinner by rememberSaveable { mutableIntStateOf(initial.dinnerMinutes) }
    var breakfastOn by rememberSaveable { mutableStateOf(initial.breakfastOn) }
    var lunchOn by rememberSaveable { mutableStateOf(initial.lunchOn) }
    var dinnerOn by rememberSaveable { mutableStateOf(initial.dinnerOn) }
    var fastingMode by rememberSaveable { mutableStateOf(initial.fastingMode) }
    var fastingRecurrence by rememberSaveable { mutableStateOf(initial.fastingRecurrence) }
    var fastStart by rememberSaveable { mutableIntStateOf(initial.fastStartMinutes) }
    var fastEnd by rememberSaveable { mutableIntStateOf(initial.fastEndMinutes) }
    var suhoorReminder by rememberSaveable { mutableStateOf(initial.suhoorReminder) }

    val sleepError = wake == sleep
    val draft = initial.copy(
        wakeMinutes = wake,
        sleepMinutes = sleep,
        workModeEnabled = workEnabled,
        workStartMinutes = workStart,
        workEndMinutes = workEnd,
        mealsEnabled = mealsEnabled,
        breakfastMinutes = breakfast,
        lunchMinutes = lunch,
        dinnerMinutes = dinner,
        breakfastOn = breakfastOn,
        lunchOn = lunchOn,
        dinnerOn = dinnerOn,
        fastingMode = fastingMode,
        fastingRecurrence = fastingRecurrence,
        fastStartMinutes = fastStart,
        fastEndMinutes = fastEnd,
        suhoorReminder = suhoorReminder
    )
    val result = HydrationCalculator.calculate(draft, lang)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(tr("🗓️ جدولي", "🗓️ Schedule"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

        // Fasting
        Section(tr("⏳ نظام الصيام", "⏳ Fasting mode")) {
            Text(tr("النمط", "Mode"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FastingMode.entries.forEach {
                    FilterChip(selected = fastingMode == it, onClick = { fastingMode = it }, label = { Text(it.label(lang)) })
                }
            }
            if (fastingMode != FastingMode.NONE) {
                Text(tr("التكرار", "Recurrence"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingRecurrence.entries.forEach {
                        FilterChip(selected = fastingRecurrence == it, onClick = { fastingRecurrence = it }, label = { Text(it.label(lang)) })
                    }
                }
                SchedTimeRow(tr("🌅 بدء الصيام", "🌅 Fast start"), fastStart) { fastStart = it }
                SchedTimeRow(tr("🌇 انتهاء الصيام", "🌇 Fast end"), fastEnd) { fastEnd = it }
                if (fastingMode == FastingMode.DRY_FAST) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("تنبيه ما قبل الصيام", "Pre-fast reminder"), fontWeight = FontWeight.SemiBold)
                            Text(
                                tr("تذكير أخير بالشرب قبل بدء الصيام بـ 30 دقيقة", "Final drink nudge 30 min before fast starts"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = suhoorReminder, onCheckedChange = { suhoorReminder = it })
                    }
                    Text(
                        tr("تتوقف التنبيهات خلال ساعات الصيام، ثم تُوزع بانتظام بعد انتهاء الصيام.", "Reminders pause during fasting hours, then distribute evenly after fast ends."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        tr("الصيام المتقطع يضيف +10% للهدف ويتجاهل توقف الوجبات ضمن نافذة الصيام.", "Intermittent fasting adds +10% to the goal and ignores meal pauses inside the fasting window."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Wake / Sleep / Work
        Section(tr("🕒 اليقظة والنوم والعمل", "🕒 Wake, sleep & work")) {
            SchedTimeRow(tr("☀️ وقت الاستيقاظ", "☀️ Wake-up time"), wake) { wake = it }
            SchedTimeRow(tr("🌙 وقت النوم", "🌙 Bedtime"), sleep) { sleep = it }
            if (sleepError) {
                Text(tr("وقت النوم يجب أن يختلف عن وقت الاستيقاظ", "Bedtime must differ from wake-up time"), color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    tr("ساعات اليقظة: ${formatDuration(result.awakeMinutes, lang)}", "Waking hours: ${formatDuration(result.awakeMinutes, lang)}"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("💼 وضع العمل الهادئ", "💼 Quiet work mode"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("اهتزاز ونغمة خفيفة خلال ساعات العمل", "Vibration and a soft chime during work hours"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = workEnabled, onCheckedChange = { workEnabled = it })
            }
            if (workEnabled) {
                SchedTimeRow(tr("بداية العمل", "Work start"), workStart) { workStart = it }
                SchedTimeRow(tr("نهاية العمل", "Work end"), workEnd) { workEnd = it }
            }
        }

        // Meal pauses (auto-disabled during a dry fast)
        Section(tr("🍽️ التوقف حول الوجبات", "🍽️ Meal pauses")) {
            val mealsLocked = fastingMode == FastingMode.DRY_FAST
            if (mealsLocked) {
                Text(
                    tr(
                        "متوقف مؤقتاً أثناء تفعيل الصيام الجاف (تُطبق راحة وجبة 45 دقيقة تلقائياً بعد انتهاء الصيام).",
                        "Paused automatically while Dry Fast is active (a 45-min meal break is applied after Fast end)."
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = HydroWarn
                )
            }
            Column(Modifier.alpha(if (mealsLocked) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("إيقاف التنبيهات وقت الأكل", "Pause reminders at meal times"), fontWeight = FontWeight.SemiBold)
                        Text(
                            tr("يتوقف التنبيه 30 دقيقة قبل الوجبة و45 دقيقة بعدها.", "Reminders stop 30 min before and 45 min after each meal."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = mealsEnabled, enabled = !mealsLocked, onCheckedChange = { mealsEnabled = it })
                }
                if (mealsEnabled) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SchedMealRow(tr("🍳 الفطور", "🍳 Breakfast"), breakfastOn, breakfast, mealsLocked, { breakfastOn = it }) { breakfast = it }
                    SchedMealRow(tr("🍚 الغداء", "🍚 Lunch"), lunchOn, lunch, mealsLocked, { lunchOn = it }) { lunch = it }
                    SchedMealRow(tr("🍲 العشاء", "🍲 Dinner"), dinnerOn, dinner, mealsLocked, { dinnerOn = it }) { dinner = it }
                }
            }
        }

        // Meetings
        Section(tr("📅 الاجتماعات", "📅 Meetings")) {
            val fmtDay = remember(lang) { SimpleDateFormat("EEE d MMM", if (lang == com.hydroforce.pro.data.Language.EN) Locale.ENGLISH else Locale("ar")) }
            val fmtTime = remember(lang) { SimpleDateFormat("hh:mm a", if (lang == com.hydroforce.pro.data.Language.EN) Locale.ENGLISH else Locale("ar")) }
            if (meetings.isEmpty()) {
                Text(tr("لا اجتماعات مجدولة — تُؤجَّل التنبيهات تلقائياً خلالها.", "No meetings — reminders auto-postpone during them."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                meetings.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title.ifBlank { tr("اجتماع", "Meeting") }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${fmtDay.format(Date(m.startMillis))} • ${fmtTime.format(Date(m.startMillis))}–${fmtTime.format(Date(m.endMillis))}", fontSize = 12.sp, color = HydroBlue)
                        }
                        androidx.compose.material3.TextButton(onClick = { onRemoveMeeting(m.id) }) { Text(tr("حذف", "Delete"), color = HydroDanger) }
                    }
                }
            }
            OutlinedButton(onClick = onOpenMeetings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(tr("➕ إضافة / إدارة الاجتماعات", "➕ Add / manage meetings"))
            }
        }

        Button(
            onClick = { onSave(draft) },
            enabled = !sleepError,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text(tr("حفظ الجدول ✅", "Save schedule ✅"), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SchedTimeRow(label: String, minutes: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(
            onClick = { TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, false).show() },
            border = BorderStroke(1.dp, HydroBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(formatClock(minutes, lang)) }
    }
}

@Composable
private fun SchedMealRow(
    label: String,
    enabled: Boolean,
    minutes: Int,
    locked: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPick: (Int) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalLanguage.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, enabled = !locked, onCheckedChange = onEnabledChange)
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, false).show() },
            enabled = enabled && !locked,
            border = BorderStroke(1.dp, HydroBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(formatClock(minutes, lang)) }
    }
}
