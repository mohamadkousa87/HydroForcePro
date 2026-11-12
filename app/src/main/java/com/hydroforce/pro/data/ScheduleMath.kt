package com.hydroforce.pro.data

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

    /** Start of the current calendar day (local midnight). Daily progress resets here. */
    fun calendarDayStart(t: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

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

    fun minuteOfDay(t: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(t).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    fun isWorkTime(t: Long, p: UserProfile, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!p.workModeEnabled) return false
        val m = minuteOfDay(t, zone)
        val s = p.workStartMinutes
        val e = p.workEndMinutes
        return if (s <= e) m in s until e else m >= s || m < e
    }
}
