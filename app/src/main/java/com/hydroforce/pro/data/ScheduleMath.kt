package com.hydroforce.pro.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure time arithmetic for the waking window (wake -> bedtime). Bedtime may fall after midnight.
 * No reminder is ever placed outside a waking window.
 */
object ScheduleMath {

    data class Window(val start: Long, val end: Long)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun windowFor(date: LocalDate, p: UserProfile, zone: ZoneId): Window {
        val start = date.atTime(LocalTime.of(p.wakeMinutes / 60, p.wakeMinutes % 60))
            .atZone(zone).toInstant().toEpochMilli()
        val awake = HydrationCalculator.awakeMinutes(p.wakeMinutes, p.sleepMinutes)
        val end = if (p.wakeMinutes == p.sleepMinutes) start + DAY_MS else start + awake * 60_000L
        return Window(start, end)
    }

    private fun windowsAround(t: Long, p: UserProfile, zone: ZoneId): List<Window> {
        val date = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        return listOf(date.minusDays(1), date, date.plusDays(1), date.plusDays(2))
            .map { windowFor(it, p, zone) }
    }

    fun windowContaining(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Window? =
        windowsAround(t, p, zone).firstOrNull { t >= it.start && t < it.end }

    fun nextWindowStart(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Long =
        windowsAround(t, p, zone).first { it.start > t }.start

    /** Start of the current hydration day: the most recent wake-up time at or before [t]. */
    fun dayStart(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Long =
        windowsAround(t, p, zone).last { it.start <= t }.start

    /** Start of the current calendar day (local midnight). */
    fun calendarDayStart(t: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Start of the CURRENT hydration cycle, anchored on the user's wake-up time (not midnight),
     * so people who stay up past 00:00 don't get their daily progress reset at midnight.
     * - If now is at/after today's wake time → today's wake time.
     * - If now is before today's wake time (e.g. 01:00 while wake is 09:00) → yesterday's wake time.
     */
    fun currentHydrationCycleStart(nowMillis: Long, wakeMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val wakeTime = LocalTime.of(wakeMinutes / 60, wakeMinutes % 60)
        val wakeToday = today.atTime(wakeTime).atZone(zone).toInstant().toEpochMilli()
        return if (nowMillis >= wakeToday) {
            wakeToday
        } else {
            today.minusDays(1).atTime(wakeTime).atZone(zone).toInstant().toEpochMilli()
        }
    }

    fun isAwake(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        windowContaining(t, p, zone) != null

    /** Next reminder [intervalMinutes] after [anchor], pushed to the next wake-up if it would land after bedtime. */
    fun nextReminder(anchor: Long, intervalMinutes: Int, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Long {
        val window = windowContaining(anchor, p, zone) ?: return nextWindowStart(anchor, p, zone)
        val candidate = anchor + intervalMinutes * 60_000L
        return if (candidate < window.end) candidate else nextWindowStart(anchor, p, zone)
    }

    /**
     * If [t] falls inside a meal-pause window (30 min before to 45 min after an enabled meal),
     * returns the absolute end of that window; otherwise null. Checks the current and previous day
     * so a window spanning midnight is still caught.
     */
    fun mealPauseEnd(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val meals = p.enabledMealTimes()
        if (meals.isEmpty()) return null
        val date = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        for (d in listOf(date.minusDays(1), date)) {
            for (meal in meals) {
                val mealAt = d.atTime(LocalTime.of(meal / 60, meal % 60))
                    .atZone(zone).toInstant().toEpochMilli()
                val start = mealAt - MEAL_PAUSE_BEFORE_MINUTES * 60_000L
                val end = mealAt + MEAL_PAUSE_AFTER_MINUTES * 60_000L
                if (t in start until end) return end
            }
        }
        return null
    }

    /** The next epoch-ms at which the clock shows [minuteOfDay], at or after [now]. */
    fun nextTimeAt(now: Long, minuteOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayAt = date.atTime(LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
        return if (todayAt >= now) todayAt
        else date.plusDays(1).atTime(LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    fun minuteOfDay(t: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(t).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    /** Whether a fast is active on the day of [t], per the chosen recurrence. */
    fun isFastingDay(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (p.fastingMode == FastingMode.NONE) return false
        return when (p.fastingRecurrence) {
            FastingRecurrence.DAILY, FastingRecurrence.TODAY_ONLY -> true
            FastingRecurrence.MON_THU -> {
                val dow = Instant.ofEpochMilli(t).atZone(zone).dayOfWeek
                dow == DayOfWeek.MONDAY || dow == DayOfWeek.THURSDAY
            }
        }
    }

    /** True when [t]'s time-of-day is inside the fast window [fastStart, fastEnd] (handles overnight). */
    fun inFastWindow(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val m = minuteOfDay(t, zone)
        val s = p.fastStartMinutes
        val e = p.fastEndMinutes
        return if (s <= e) m in s until e else m >= s || m < e
    }

    /** Absolute epoch-ms of the fast end (iftar) for the fast window covering [t]. */
    fun fastEndFor(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Long {
        val s = p.fastStartMinutes
        val e = p.fastEndMinutes
        val m = minuteOfDay(t, zone)
        val date = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val endDate = if (s <= e) date else if (m >= s) date.plusDays(1) else date
        return endDate.atTime(LocalTime.of(e / 60, e % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    fun isWorkTime(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!p.workModeEnabled) return false
        val m = minuteOfDay(t, zone)
        val s = p.workStartMinutes
        val e = p.workEndMinutes
        return if (s <= e) m in s until e else m >= s || m < e
    }
}
