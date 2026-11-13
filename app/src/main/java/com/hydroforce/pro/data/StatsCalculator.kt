package com.hydroforce.pro.data

import java.time.ZoneId

/** One calendar day's hydration summary. */
data class DayStat(
    val dayStartMillis: Long,
    val totalMl: Int,
    val goalMet: Boolean
)

/** Aggregated stats for the dashboard stats screen. */
data class StatsData(
    val days: List<DayStat>,
    val targetMl: Int,
    val streak: Int,
    val totalLoggedMl: Int,
    val avgPerDayMl: Int,
    val daysGoalMet: Int,
    val avgUrineGrade: Double,
    val bathroomCount: Int
)

object StatsCalculator {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun compute(
        logs: List<IntakeLog>,
        profile: UserProfile,
        now: Long,
        days: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
        bioLogs: List<BioCheckLog> = emptyList()
    ): StatsData {
        val target = HydrationCalculator.calculate(profile).targetMl.coerceAtLeast(1)
        val todayStart = ScheduleMath.calendarDayStart(now, zone)

        fun totalFor(dayStart: Long): Int =
            logs.filter { it.timestamp >= dayStart && it.timestamp < dayStart + DAY_MS }
                .sumOf { it.effectiveMl }

        // Oldest -> newest across the window (chart reads left to right in RTL too).
        val dayStats = (0 until days).map { i ->
            val start = todayStart - (days - 1 - i) * DAY_MS
            val total = totalFor(start)
            DayStat(start, total, total >= target)
        }

        // Streak: consecutive days meeting the goal. Today is allowed to be in progress.
        var streak = 0
        var cursor = todayStart
        if (totalFor(todayStart) < target) cursor -= DAY_MS
        while (totalFor(cursor) >= target) {
            streak++
            cursor -= DAY_MS
        }

        val totalLogged = logs.sumOf { it.amountMl }
        val avg = dayStats.sumOf { it.totalMl } / days
        val metCount = dayStats.count { it.goalMet }

        val windowStart = todayStart - (days - 1) * DAY_MS
        val windowBio = bioLogs.filter { it.timestamp >= windowStart && it.timestamp < todayStart + DAY_MS }
        val avgUrine = if (windowBio.isEmpty()) 0.0 else windowBio.map { it.urineColorGrade }.average()

        return StatsData(
            days = dayStats,
            targetMl = target,
            streak = streak,
            totalLoggedMl = totalLogged,
            avgPerDayMl = avg,
            daysGoalMet = metCount,
            avgUrineGrade = avgUrine,
            bathroomCount = windowBio.size
        )
    }
}
