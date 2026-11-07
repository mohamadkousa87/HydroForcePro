package com.hydroforce.pro.alarm

import android.content.Context
import com.hydroforce.pro.data.AppDatabase
import com.hydroforce.pro.data.CALL_POSTPONE_MINUTES
import com.hydroforce.pro.data.DrinkType
import com.hydroforce.pro.data.HydrationCalculator
import com.hydroforce.pro.data.IntakeLog
import com.hydroforce.pro.data.effectiveMlFor
import com.hydroforce.pro.data.MAX_SNOOZES
import com.hydroforce.pro.data.Meeting
import com.hydroforce.pro.data.RETRY_MINUTES
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.UserProfile
import com.hydroforce.pro.data.covering

/** Single place that mutates intake + alarm state, shared by activities, the service and receivers. */
object HydrationManager {

    const val SOURCE_ALARM = "alarm"
    const val SOURCE_MANUAL = "manual"

    /**
     * Pushes [at] out of sleep windows and scheduled meetings: resumes right after a meeting ends,
     * and never lands outside the waking window.
     */
    private fun adjustForMeetings(at: Long, meetings: List<Meeting>, profile: UserProfile): Long {
        var t = at
        var guard = 0
        while (guard++ < 40) {
            if (!ScheduleMath.isAwake(t, profile)) {
                t = ScheduleMath.nextWindowStart(t, profile)
                continue
            }
            val m = meetings.covering(t) ?: break
            t = m.endMillis
        }
        return t
    }

    /** Applies meeting/window adjustment to [base], then arms the alarm and persists it. */
    private suspend fun arm(context: Context, repo: SettingsRepository, profile: UserProfile, base: Long): Long {
        val at = adjustForMeetings(base, repo.currentMeetings(), profile)
        AlarmScheduler.schedule(context, at)
        repo.setNextAlarm(at)
        return at
    }

    /**
     * Cancels any pending alarm and schedules the next one [interval] after [anchor],
     * respecting bedtime and meetings. If today's goal is already met, waits for the next wake-up.
     */
    suspend fun rescheduleFrom(context: Context, anchor: Long = System.currentTimeMillis()): Long {
        val repo = SettingsRepository(context)
        if (!repo.isOnboarded()) return 0L
        val profile = repo.currentProfile()
        val result = HydrationCalculator.calculate(profile)
        val consumed = AppDatabase.get(context).intakeDao()
            .totalSince(ScheduleMath.calendarDayStart(anchor))
        val base = if (consumed >= result.targetMl) {
            ScheduleMath.nextWindowStart(anchor, profile)
        } else {
            ScheduleMath.nextReminder(anchor, result.intervalMinutes, profile)
        }
        return arm(context, repo, profile, base)
    }

    /** Called only after ML Kit confirmed a cup/bottle in the live camera frame. */
    suspend fun recordVerifiedDrink(context: Context, source: String, drinkType: DrinkType = DrinkType.WATER) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val now = System.currentTimeMillis()
        val amount = profile.cupSizeMl
        AppDatabase.get(context).intakeDao().insert(
            IntakeLog(
                timestamp = now,
                amountMl = amount,
                source = source,
                drinkType = drinkType.name,
                effectiveMl = effectiveMlFor(amount, drinkType)
            )
        )
        repo.setLastDrink(now)
        repo.setSnoozeUsed(0)
        rescheduleFrom(context, now)
    }

    /**
     * Snooze for [minutes], limited to [MAX_SNOOZES] per alarm cycle. Returns false when exhausted.
     * Stays inside the waking window and skips meetings.
     */
    suspend fun snooze(context: Context, minutes: Int): Boolean {
        val repo = SettingsRepository(context)
        val used = repo.currentAlarmState().snoozeUsed
        if (used >= MAX_SNOOZES) return false
        val profile = repo.currentProfile()
        val base = ScheduleMath.nextReminder(System.currentTimeMillis(), minutes, profile)
        repo.setSnoozeUsed(used + 1)
        arm(context, repo, profile, base)
        return true
    }

    /** Re-reminder after an alarm rang unanswered; stays inside the waking window. */
    suspend fun scheduleRetry(context: Context) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val base = ScheduleMath.nextReminder(System.currentTimeMillis(), RETRY_MINUTES, profile)
        arm(context, repo, profile, base)
    }

    /**
     * Postpone the reminder by [minutes] without using a snooze — used when the alarm fires
     * while the user is on a call. Stays inside the waking window and skips meetings.
     */
    suspend fun schedulePostpone(context: Context, minutes: Int = CALL_POSTPONE_MINUTES) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val base = ScheduleMath.nextReminder(System.currentTimeMillis(), minutes, profile)
        arm(context, repo, profile, base)
    }

    /** Postpone the reminder until at least [target] (e.g. the end of a meeting). */
    suspend fun schedulePostponeUntil(context: Context, target: Long) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val base = maxOf(target, System.currentTimeMillis() + 60_000L)
        arm(context, repo, profile, base)
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
