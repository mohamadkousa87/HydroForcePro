package com.hydroforce.pro.alarm

import android.content.Context
import com.hydroforce.pro.data.AppDatabase
import com.hydroforce.pro.data.HydrationCalculator
import com.hydroforce.pro.data.IntakeLog
import com.hydroforce.pro.data.MAX_SNOOZES
import com.hydroforce.pro.data.SNOOZE_MINUTES
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.SettingsRepository

/** Single place that mutates intake + alarm state, shared by activities, the service and receivers. */
object HydrationManager {

    const val SOURCE_ALARM = "alarm"
    const val SOURCE_MANUAL = "manual"

    /**
     * Cancels any pending alarm and schedules the next one [interval] after [anchor],
     * respecting bedtime. If today's goal is already met, waits for the next wake-up.
     */
    suspend fun rescheduleFrom(context: Context, anchor: Long = System.currentTimeMillis()): Long {
        val repo = SettingsRepository(context)
        if (!repo.isOnboarded()) return 0L
        val profile = repo.currentProfile()
        val result = HydrationCalculator.calculate(profile)
        val consumed = AppDatabase.get(context).intakeDao()
            .totalSince(ScheduleMath.dayStart(anchor, profile))
        val next = if (consumed >= result.targetMl) {
            ScheduleMath.nextWindowStart(anchor, profile)
        } else {
            ScheduleMath.nextReminder(anchor, result.intervalMinutes, profile)
        }
        AlarmScheduler.schedule(context, next)
        repo.setNextAlarm(next)
        return next
    }

    /** Called only after ML Kit confirmed a cup/bottle in the live camera frame. */
    suspend fun recordVerifiedDrink(context: Context, source: String) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val now = System.currentTimeMillis()
        AppDatabase.get(context).intakeDao()
            .insert(IntakeLog(timestamp = now, amountMl = profile.cupSizeMl, source = source))
        repo.setLastDrink(now)
        repo.setSnoozeUsed(0)
        rescheduleFrom(context, now)
    }

    /** Emergency snooze, limited to [MAX_SNOOZES] per alarm cycle. Returns false when exhausted. */
    suspend fun snooze(context: Context): Boolean {
        val repo = SettingsRepository(context)
        val used = repo.currentAlarmState().snoozeUsed
        if (used >= MAX_SNOOZES) return false
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        repo.setSnoozeUsed(used + 1)
        AlarmScheduler.schedule(context, at)
        repo.setNextAlarm(at)
        return true
    }

    /** Re-reminder after an alarm rang unanswered; stays inside the waking window. */
    suspend fun scheduleRetry(context: Context) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val at = ScheduleMath.nextReminder(System.currentTimeMillis(), SNOOZE_MINUTES, profile)
        AlarmScheduler.schedule(context, at)
        repo.setNextAlarm(at)
    }

    suspend fun markAlarmRinging(context: Context) {
        SettingsRepository(context).setNextAlarm(0L)
    }

    /** After boot / time change: keep a future alarm, otherwise compute a fresh one. */
    suspend fun restore(context: Context) {
        val repo = SettingsRepository(context)
        if (!repo.isOnboarded()) return
        val next = repo.currentAlarmState().nextAlarmAt
        if (next > System.currentTimeMillis()) {
            AlarmScheduler.schedule(context, next)
        } else {
            rescheduleFrom(context)
        }
    }
}
