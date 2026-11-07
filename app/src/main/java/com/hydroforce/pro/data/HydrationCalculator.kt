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

    fun calculate(p: UserProfile, lang: Language = Language.AR): HydrationResult {
        val en = lang == Language.EN
        val items = mutableListOf<BreakdownItem>()
        val factor = mlPerKg(p.age)
        val base = (p.weightKg * factor).roundToInt()
        val ageBand = when {
            p.age > 65 -> if (en) "over 65 years" else "أكبر من 65 سنة"
            p.age > 55 -> if (en) "55 to 65 years" else "من 55 إلى 65 سنة"
            p.age < 18 -> if (en) "under 18 (adult factor used)" else "أقل من 18 سنة (يُعتمد معامل البالغين)"
            else -> if (en) "18 to 55 years" else "من 18 إلى 55 سنة"
        }
        items += BreakdownItem(
            if (en) "Base need by weight & age" else "الاحتياج الأساسي حسب الوزن والعمر",
            if (en) "${formatNum(p.weightKg)} kg × $factor ml (age group: $ageBand)"
            else "${formatNum(p.weightKg)} كغ × $factor مل (الفئة العمرية: $ageBand)",
            base
        )
        items += if (p.gender == Gender.MALE) {
            BreakdownItem(
                if (en) "Gender factor" else "معامل الجنس",
                if (en) "Males have higher muscle mass needing more water" else "الذكور لديهم نسبة كتلة عضلية أعلى تحتاج ماءً أكثر",
                250
            )
        } else {
            BreakdownItem(
                if (en) "Gender factor" else "معامل الجنس",
                if (en) "No addition for females" else "لا توجد إضافة للإناث",
                0
            )
        }
        items += if (p.heightCm > 175) {
            BreakdownItem(
                if (en) "Height / body surface area factor" else "معامل الطول / مساحة سطح الجسم",
                if (en) "Height ${p.heightCm} cm over 175 cm → larger surface, more fluid loss"
                else "الطول ${p.heightCm} سم أكبر من 175 سم ← مساحة سطح أكبر وفقدان أعلى للسوائل",
                100
            )
        } else {
            BreakdownItem(
                if (en) "Height / body surface area factor" else "معامل الطول / مساحة سطح الجسم",
                if (en) "Height ${p.heightCm} cm within range" else "الطول ${p.heightCm} سم ضمن المعدل",
                0
            )
        }
        items += BreakdownItem(
            (if (en) "Physical activity level: " else "مستوى النشاط البدني: ") + p.activity.label(lang),
            p.activity.description(lang),
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

fun formatClock(minutes: Int, lang: Language = Language.AR): String {
    val h = minutes / 60
    val m = minutes % 60
    val en = lang == Language.EN
    val suffix = if (h < 12) (if (en) "AM" else "ص") else (if (en) "PM" else "م")
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return String.format(Locale.US, "%02d:%02d %s", h12, m, suffix)
}

fun formatDuration(minutes: Int, lang: Language = Language.AR): String {
    val h = minutes / 60
    val m = minutes % 60
    val en = lang == Language.EN
    return when {
        h == 0 -> if (en) "$m min" else "$m دقيقة"
        m == 0 -> if (en) "$h h" else "$h ساعة"
        else -> if (en) "$h h $m min" else "$h ساعة و $m دقيقة"
    }
}
