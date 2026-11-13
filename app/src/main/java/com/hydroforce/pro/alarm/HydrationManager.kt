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
import com.hydroforce.pro.widget.updateAllHydroWidgets
import kotlin.math.roundToInt

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
        while (guard++ < 60) {
            if (!ScheduleMath.isAwake(t, profile)) {
                t = ScheduleMath.nextWindowStart(t, profile)
                continue
            }
            val mealEnd = ScheduleMath.mealPauseEnd(t, profile)
            if (mealEnd != null) {
                t = mealEnd
                continue
            }
            val m = meetings.covering(t)
            if (m != null) {
                t = m.endMillis
                continue
            }
            break
        }
        return t
    }

    /** Applies meeting/window adjustment to [base], then arms the alarm and persists it. */
    private suspend fun arm(context: Context, repo: SettingsRepository, profile: UserProfile, base: Long): Long {
        val at = adjustForMeetings(base, repo.currentMeetings(), profile)
        AlarmScheduler.schedule(context, at)
        repo.setNextAlarm(at)
        updateAllHydroWidgets(context)
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
            .totalSince(ScheduleMath.currentHydrationCycleStart(anchor, profile.wakeMinutes))
        val base = if (consumed >= result.targetMl) {
            ScheduleMath.nextWindowStart(anchor, profile)
        } else {
            ScheduleMath.nextReminder(anchor, result.intervalMinutes, profile)
        }
        return arm(context, repo, profile, base)
    }

    /**
     * After a settings save: keep the running countdown when a valid future alarm already exists
     * inside a waking window; only recompute when there's none or it's now invalid. This prevents
     * saving the profile (even with no real change) from resetting the countdown.
     */
    suspend fun rescheduleKeepingCountdown(context: Context) {
        val repo = SettingsRepository(context)
        if (!repo.isOnboarded()) return
        val profile = repo.currentProfile()
        val next = repo.currentAlarmState().nextAlarmAt
        val now = System.currentTimeMillis()
        if (next > now && ScheduleMath.isAwake(next, profile)) {
            AlarmScheduler.schedule(context, next)
            updateAllHydroWidgets(context)
        } else {
            rescheduleFrom(context)
        }
    }

    /**
     * Called only after ML Kit confirmed a cup/bottle. [amountMl] is the actual volume the user
     * picked in the camera screen. Uses the "water time credit" model: drinking more buys more time,
     * and a proactive partial drink extends (never shortens) the next reminder.
     */
    suspend fun recordVerifiedDrink(
        context: Context,
        amountMl: Int,
        source: String,
        drinkType: DrinkType = DrinkType.WATER
    ) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val now = System.currentTimeMillis()
        val amount = amountMl.coerceAtLeast(1)
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

        val result = HydrationCalculator.calculate(profile)
        val consumed = AppDatabase.get(context).intakeDao()
            .totalSince(ScheduleMath.currentHydrationCycleStart(now, profile.wakeMinutes))
        // Goal met → wait for the next waking window instead of another reminder today.
        if (consumed >= result.targetMl) {
            arm(context, repo, profile, ScheduleMath.nextWindowStart(now, profile))
            return
        }

        val baseIntervalMinutes = result.intervalMinutes
        val earnedMinutes = (baseIntervalMinutes * (amount.toDouble() / profile.cupSizeMl))
            .roundToInt()
            .coerceAtLeast(30)
        val ringing = AlarmService.ringing.value
        val nextTrigger = repo.currentAlarmState().nextAlarmAt

        val base = if (ringing || nextTrigger <= now) {
            // Responding to a live/overdue alarm: next reminder is earned time from now.
            now + earnedMinutes * 60_000L
        } else {
            // Proactive drink before the next alarm is due: extend it, capped at one full interval from now.
            val candidate = maxOf(nextTrigger + earnedMinutes * 60_000L, now + earnedMinutes * 60_000L)
            val maxAllowed = now + baseIntervalMinutes * 60_000L
            minOf(candidate, maxAllowed)
        }
        arm(context, repo, profile, base)
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
