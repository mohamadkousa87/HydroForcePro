package com.hydroforce.pro.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hydroforce.pro.MainActivity
import com.hydroforce.pro.data.AppDatabase
import com.hydroforce.pro.data.FastingMode
import com.hydroforce.pro.data.HydrationCalculator
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.VolumeUnit
import com.hydroforce.pro.data.formatVolume
import com.hydroforce.pro.ui.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DEEP = Color(0xFF0B4F8A)
private val BLUE = Color(0xFF0B6FC2)
private val SKY = Color(0xFF7FD3FF)
private val WHITE = Color(0xFFFFFFFF)

private data class WidgetData(
    val consumed: Int,
    val target: Int,
    val percent: Int,
    val nextAlarmAt: Long,
    val onboarded: Boolean,
    val lang: Language,
    val unit: VolumeUnit,
    val mode: String
)

private suspend fun loadWidgetData(context: Context): WidgetData {
    val repo = SettingsRepository(context)
    val lang = repo.currentLanguage()
    if (!repo.isOnboarded()) return WidgetData(0, 0, 0, 0L, false, lang, VolumeUnit.ML, "")
    val profile = repo.currentProfile()
    val now = System.currentTimeMillis()
    val cycleStart = ScheduleMath.currentHydrationCycleStart(now, profile.wakeMinutes)
    val dao = AppDatabase.get(context).intakeDao()
    val result = HydrationCalculator.calculate(profile)
    val base = result.targetMl + dao.bioBonusSince(cycleStart) + repo.workoutBonusFor(cycleStart)
    val consumed = dao.totalSince(cycleStart)
    val alarm = repo.currentAlarmState()
    val percent = if (base > 0) (consumed * 100 / base).coerceIn(0, 100) else 0
    val mode = when {
        repo.currentWorkoutEnd() > now -> tr(lang, "تمرين", "Workout")
        profile.fastingMode != FastingMode.NONE && ScheduleMath.isFastingDay(now, profile) -> tr(lang, "صيام", "Fasting")
        ScheduleMath.isWorkTime(now, profile) -> tr(lang, "عمل", "Work")
        else -> tr(lang, "عادي", "Normal")
    }
    return WidgetData(consumed, base, percent, alarm.nextAlarmAt, true, lang, profile.volumeUnit, mode)
}

private fun openApp(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

/** Refreshes every widget; call whenever intake or the next alarm changes. */
suspend fun updateAllHydroWidgets(context: Context) {
    TodayProgressWidget().updateAll(context)
    NextReminderWidget().updateAll(context)
    QuickButtonWidget().updateAll(context)
}

// ---------- Widget 1: today's progress ----------

class TodayProgressWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadWidgetData(context)
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(DEEP).cornerRadius(20.dp)
                    .padding(14.dp).clickable(openApp(context)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tr(d.lang, "💧 تقدم اليوم", "💧 Today"),
                    style = TextStyle(color = ColorProvider(SKY), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    "${d.percent}%",
                    style = TextStyle(color = ColorProvider(WHITE), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    if (d.onboarded) "${formatVolume(d.consumed, d.unit, d.lang)} / ${formatVolume(d.target, d.unit, d.lang)}"
                    else tr(d.lang, "افتح التطبيق للإعداد", "Open the app to set up"),
                    style = TextStyle(color = ColorProvider(WHITE), fontSize = 13.sp)
                )
            }
        }
    }
}

class TodayProgressReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayProgressWidget()
}

// ---------- Widget 2: next reminder ----------

class NextReminderWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadWidgetData(context)
        val locale = if (d.lang == Language.EN) Locale.ENGLISH else Locale("ar")
        val timeText = when {
            !d.onboarded -> "—"
            d.nextAlarmAt <= System.currentTimeMillis() -> tr(d.lang, "قريباً", "Soon")
            else -> SimpleDateFormat("hh:mm a", locale).format(Date(d.nextAlarmAt))
        }
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(BLUE).cornerRadius(20.dp)
                    .padding(14.dp).clickable(openApp(context)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tr(d.lang, "⏱️ التنبيه القادم", "⏱️ Next reminder"),
                    style = TextStyle(color = ColorProvider(WHITE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    timeText,
                    style = TextStyle(color = ColorProvider(WHITE), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    if (d.onboarded) d.mode else tr(d.lang, "شرب الماء 💧", "Drink water 💧"),
                    style = TextStyle(color = ColorProvider(SKY), fontSize = 12.sp)
                )
            }
        }
    }
}

class NextReminderReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextReminderWidget()
}

// ---------- Widget 3: quick open button ----------

class QuickButtonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadWidgetData(context)
        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(BLUE).cornerRadius(24.dp)
                    .clickable(openApp(context)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tr(d.lang, "💧 شربت ماء", "💧 I drank water"),
                    style = TextStyle(color = ColorProvider(WHITE), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

class QuickButtonReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickButtonWidget()
}
