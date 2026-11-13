package com.hydroforce.pro.alarm

import android.content.Context
import com.hydroforce.pro.data.AppDatabase
import com.hydroforce.pro.data.BioCheckLog
import com.hydroforce.pro.data.CALL_POSTPONE_MINUTES
import com.hydroforce.pro.data.DrinkType
import com.hydroforce.pro.data.FastingMode
import com.hydroforce.pro.data.FastingRecurrence
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
    /** Recorded when the user used the emergency "sip promise" failsafe (AI couldn't detect the cup). */
    const val SOURCE_PROMISE = "promise"
    /** Auto-logged gym sip pulse. */
    const val SOURCE_WORKOUT = "workout"
    const val WORKOUT_SWEAT_BONUS_ML = 500
    const val BIO_BONUS_CAP_ML = 500

    /** Result of logging a urine bio-check, used by the UI to show messages/open camera. */
    data class BioOutcome(val grade: Int, val addedMl: Int, val openCamera: Boolean, val flushWarning: Boolean)

    /**
     * Pushes [at] out of sleep windows and scheduled meetings: resumes right after a meeting ends,
     * and never lands outside the waking window.
     */
    private fun adjustForMeetings(at: Long, meetings: List<Meeting>, profile: UserProfile): Long {
        var t = at
        var guard = 0
        // Safety cap to avoid any infinite loop across sleep/fast/meal/meeting pushes.
        while (guard++ < 20) {
            if (!ScheduleMath.isAwake(t, profile)) {
                t = ScheduleMath.nextWindowStart(t, profile)
                continue
            }
            // Dry fast: freeze the daytime — push any reminder to iftar (fast end).
            if (profile.fastingMode == FastingMode.DRY_FAST &&
                ScheduleMath.isFastingDay(t, profile) && ScheduleMath.inFastWindow(t, profile)
            ) {
                t = ScheduleMath.fastEndFor(t, profile)
                continue
            }
            // Intermittent fast ignores meal pauses inside the fasting window (no meals happen then).
            val skipMeals = profile.fastingMode == FastingMode.INTERMITTENT_FAST &&
                ScheduleMath.isFastingDay(t, profile) && ScheduleMath.inFastWindow(t, profile)
            if (!skipMeals) {
                val mealEnd = ScheduleMath.mealPauseEnd(t, profile)
                if (mealEnd != null) {
                    t = mealEnd
                    continue
                }
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

    /** Current cycle's goal = base medical target (incl. intermittent +10%) + bio rescue + workout sweat. */
    private suspend fun effectiveTargetMl(context: Context, repo: SettingsRepository, profile: UserProfile, anchor: Long): Int {
        val cycleStart = ScheduleMath.currentHydrationCycleStart(anchor, profile.wakeMinutes)
        val base = HydrationCalculator.calculate(profile, nowMillis = anchor).targetMl
        val dao = AppDatabase.get(context).intakeDao()
        return base + dao.bioBonusSince(cycleStart) + repo.workoutBonusFor(cycleStart)
    }

    /** TODAY_ONLY fasts auto-expire once the hydration cycle they were set in has passed. */
    private suspend fun maybeAutoEndTodayFast(context: Context, repo: SettingsRepository) {
        val p = repo.currentProfile()
        if (p.fastingMode != FastingMode.NONE && p.fastingRecurrence == FastingRecurrence.TODAY_ONLY) {
            val activated = repo.fastingActivatedCycle()
            val cycle = ScheduleMath.currentHydrationCycleStart(System.currentTimeMillis(), p.wakeMinutes)
            if (activated != 0L && cycle > activated) repo.setFastingMode(FastingMode.NONE)
        }
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
        maybeAutoEndTodayFast(context, repo)
        val profile = repo.currentProfile()
        val result = HydrationCalculator.calculate(profile, nowMillis = anchor)
        val cycleStart = ScheduleMath.currentHydrationCycleStart(anchor, profile.wakeMinutes)
        val dao = AppDatabase.get(context).intakeDao()
        val consumed = dao.totalSince(cycleStart)
        val target = result.targetMl + dao.bioBonusSince(cycleStart) + repo.workoutBonusFor(cycleStart)
        var base = if (consumed >= target) {
            ScheduleMath.nextWindowStart(anchor, profile)
        } else {
            ScheduleMath.nextReminder(anchor, result.intervalMinutes, profile)
        }
        // Keep the main alarm after an active workout.
        val workoutEnd = repo.currentWorkoutEnd()
        if (workoutEnd > anchor) base = maxOf(base, workoutEnd + 30 * 60_000L)
        // Suhoor nudge: 30 min before imsak during a dry fast.
        if (profile.fastingMode == FastingMode.DRY_FAST && profile.suhoorReminder &&
            ScheduleMath.isFastingDay(anchor, profile)
        ) {
            val suhoor = ScheduleMath.nextTimeAt(anchor, profile.fastStartMinutes - 30)
            if (suhoor > anchor) base = minOf(base, suhoor)
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

        val result = HydrationCalculator.calculate(profile, nowMillis = now)
        val cycleStart = ScheduleMath.currentHydrationCycleStart(now, profile.wakeMinutes)
        val dao = AppDatabase.get(context).intakeDao()
        val consumed = dao.totalSince(cycleStart)
        val target = result.targetMl + dao.bioBonusSince(cycleStart) + repo.workoutBonusFor(cycleStart)
        // Goal met → wait for the next waking window instead of another reminder today.
        if (consumed >= target) {
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

    // ===== Workout sip mode =====

    /**
     * Starts a workout: grants a +500 ml sweat bonus for this cycle. Outside a dry fast it also
     * postpones the main alarm past the workout and schedules 15-min sip pulses. During a dry fast
     * only the bonus is added (no sips / no camera).
     */
    suspend fun startWorkout(context: Context, durationMinutes: Int, dryFastNow: Boolean) {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val now = System.currentTimeMillis()
        val cycleStart = ScheduleMath.currentHydrationCycleStart(now, profile.wakeMinutes)
        val existing = repo.workoutBonusFor(cycleStart)
        repo.setWorkoutBonus(existing + WORKOUT_SWEAT_BONUS_ML, cycleStart)
        if (dryFastNow) {
            repo.setWorkoutEnd(0L)
            rescheduleFrom(context, now)
            return
        }
        val end = now + durationMinutes * 60_000L
        repo.setWorkoutEnd(end)
        // Postpone the main enforcer alarm until after the workout.
        arm(context, repo, profile, end + 30 * 60_000L)
        val firstSip = now + 15 * 60_000L
        if (firstSip < end) AlarmScheduler.scheduleWorkoutSip(context, firstSip)
        updateAllHydroWidgets(context)
    }

    /** Ends the workout early: stops sip pulses and restores the normal main alarm. */
    suspend fun endWorkout(context: Context) {
        val repo = SettingsRepository(context)
        repo.setWorkoutEnd(0L)
        AlarmScheduler.cancelWorkoutSip(context)
        rescheduleFrom(context)
    }

    /** Fired by a workout sip pulse: auto-logs 120 ml and schedules the next pulse. Returns true if still in workout. */
    suspend fun onWorkoutSipAlarm(context: Context): Boolean {
        val repo = SettingsRepository(context)
        val now = System.currentTimeMillis()
        val end = repo.currentWorkoutEnd()
        if (end <= now) {
            AlarmScheduler.cancelWorkoutSip(context)
            return false
        }
        AppDatabase.get(context).intakeDao().insert(
            IntakeLog(timestamp = now, amountMl = 120, source = SOURCE_WORKOUT, drinkType = DrinkType.WATER.name, effectiveMl = 120)
        )
        updateAllHydroWidgets(context)
        val next = now + 15 * 60_000L
        if (next < end) AlarmScheduler.scheduleWorkoutSip(context, next)
        return true
    }

    // ===== Daily medical tip =====

    /** Schedules one daily tip 60 min after wake-up (or cancels it when disabled). */
    suspend fun scheduleNextDailyTip(context: Context) {
        val repo = SettingsRepository(context)
        if (!repo.isOnboarded()) return
        val p = repo.currentProfile()
        if (!p.dailyTipsEnabled) {
            AlarmScheduler.cancelDailyTip(context)
            return
        }
        val at = ScheduleMath.nextTimeAt(System.currentTimeMillis(), p.wakeMinutes + 60)
        AlarmScheduler.scheduleDailyTip(context, at)
    }

    // ===== Interactive bio-check (urine color) =====

    /**
     * Logs a urine-color grade and applies rescue water (capped at [BIO_BONUS_CAP_ML] per cycle),
     * reacting to dehydration. Returns a [BioOutcome] for the UI.
     */
    suspend fun applyBioCheck(context: Context, grade: Int): BioOutcome {
        val repo = SettingsRepository(context)
        val profile = repo.currentProfile()
        val now = System.currentTimeMillis()
        val cycleStart = ScheduleMath.currentHydrationCycleStart(now, profile.wakeMinutes)
        val dao = AppDatabase.get(context).intakeDao()

        val prior = dao.bioBonusSince(cycleStart)
        val requested = when (grade) {
            in 1..2 -> 0
            in 3..4 -> 150
            in 5..6 -> 350
            else -> 500
        }
        val bonus = requested.coerceAtMost((BIO_BONUS_CAP_ML - prior).coerceAtLeast(0))
        dao.insertBioCheck(BioCheckLog(timestamp = now, urineColorGrade = grade, addedBonusMl = bonus))
        updateAllHydroWidgets(context)

        val awake = ScheduleMath.isAwake(now, profile)
        val dryFastNow = profile.fastingMode == FastingMode.DRY_FAST &&
            ScheduleMath.isFastingDay(now, profile) && ScheduleMath.inFastWindow(now, profile)

        // Grade 1 + a recent large gulp → rapid flush warning.
        val last = dao.lastIntake()
        val flushWarning = grade == 1 && last != null &&
            last.amountMl >= 350 && (now - last.timestamp) < 30 * 60_000L

        when {
            grade in 5..6 && awake && !dryFastNow -> arm(context, repo, profile, now + 30 * 60_000L)
            else -> rescheduleFrom(context, now)
        }
        return BioOutcome(grade = grade, addedMl = bonus, openCamera = grade >= 7, flushWarning = flushWarning)
    }

    suspend fun undoLastBioCheck(context: Context) {
        val dao = AppDatabase.get(context).intakeDao()
        val last = dao.lastBioCheck() ?: return
        dao.deleteBioCheck(last.id)
        updateAllHydroWidgets(context)
        rescheduleFrom(context)
    }

    /** After boot / time change: keep a future alarm, otherwise compute a fresh one; re-arm daily tip. */
    suspend fun restore(context: Context) {
        val repo = SettingsRepository(context)
        if (!repo.isOnboarded()) return
        maybeAutoEndTodayFast(context, repo)
        scheduleNextDailyTip(context)
        val next = repo.currentAlarmState().nextAlarmAt
        if (next > System.currentTimeMillis()) {
            AlarmScheduler.schedule(context, next)
        } else {
            rescheduleFrom(context)
        }
    }
}
