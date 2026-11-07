package com.hydroforce.pro.data

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class BreakdownItem(val title: String, val detail: String, val ml: Int)

data class HydrationResult(
    val targetMl: Int,
    val cupsNeeded: Int,
    val intervalMinutes: Int,
    val awakeMinutes: Int,
    val items: List<BreakdownItem>
)

object HydrationCalculator {

    fun mlPerKg(age: Int): Int = when {
        age > 65 -> 30
        age > 55 -> 33
        else -> 35
    }

    fun awakeMinutes(wake: Int, sleep: Int): Int {
        val diff = sleep - wake
        return if (diff > 0) diff else diff + 24 * 60
    }

    fun calculate(p: UserProfile): HydrationResult {
        val items = mutableListOf<BreakdownItem>()
        val factor = mlPerKg(p.age)
        val base = (p.weightKg * factor).roundToInt()
        val ageBand = when {
            p.age > 65 -> "أكبر من 65 سنة"
            p.age > 55 -> "من 55 إلى 65 سنة"
            p.age < 18 -> "أقل من 18 سنة (يُعتمد معامل البالغين)"
            else -> "من 18 إلى 55 سنة"
        }
        items += BreakdownItem(
            "الاحتياج الأساسي حسب الوزن والعمر",
            "${formatNum(p.weightKg)} كغ × $factor مل (الفئة العمرية: $ageBand)",
            base
        )
        items += if (p.gender == Gender.MALE) {
            BreakdownItem("معامل الجنس", "الذكور لديهم نسبة كتلة عضلية أعلى تحتاج ماءً أكثر", 250)
        } else {
            BreakdownItem("معامل الجنس", "لا توجد إضافة للإناث", 0)
        }
        items += if (p.heightCm > 175) {
            BreakdownItem(
                "معامل الطول / مساحة سطح الجسم",
                "الطول ${p.heightCm} سم أكبر من 175 سم ← مساحة سطح أكبر وفقدان أعلى للسوائل",
                100
            )
        } else {
            BreakdownItem("معامل الطول / مساحة سطح الجسم", "الطول ${p.heightCm} سم ضمن المعدل", 0)
        }
        items += BreakdownItem(
            "مستوى النشاط البدني: ${p.activity.label}",
            p.activity.description,
            p.activity.bonusMl
        )
        val target = items.sumOf { it.ml }
        val cups = ceil(target.toDouble() / p.cupSizeMl).toInt().coerceAtLeast(1)
        val awake = awakeMinutes(p.wakeMinutes, p.sleepMinutes)
        val interval = (awake / cups).coerceAtLeast(15)
        return HydrationResult(target, cups, interval, awake, items)
    }

    fun bmi(p: UserProfile): Float {
        val m = p.heightCm / 100f
        return if (m > 0f) p.weightKg / (m * m) else 0f
    }

    fun formatNum(f: Float): String =
        if (f % 1f == 0f) f.toInt().toString() else String.format(Locale.US, "%.1f", f)
}

fun formatMl(ml: Int): String = String.format(Locale.US, "%,d", ml)

fun formatClock(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val suffix = if (h < 12) "ص" else "م"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return String.format(Locale.US, "%02d:%02d %s", h12, m, suffix)
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m دقيقة"
        m == 0 -> "$h ساعة"
        else -> "$h ساعة و $m دقيقة"
    }
}
