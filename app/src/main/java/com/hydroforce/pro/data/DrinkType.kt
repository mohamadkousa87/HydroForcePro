package com.hydroforce.pro.data

import kotlin.math.roundToInt

/**
 * A drink category with a hydration factor: how much of its volume counts toward the daily
 * water goal (1.0 = full hydration, lower for mildly diuretic drinks).
 */
enum class DrinkType(val label: String, val emoji: String, val hydrationFactor: Float) {
    WATER("ماء", "💧", 1.0f),
    COFFEE_ESPRESSO("قهوة إسبريسو", "☕", 0.85f),
    TEA("شاي", "🍵", 0.90f),
    HERBAL("أعشاب", "🌿", 1.0f),
    COCKTAIL("كوكتيل", "🍹", 0.90f),
    LABAN("لبن", "🥛", 1.0f),
    MILK("حليب", "🥛", 1.0f),
    FRESH_JUICE("عصير فريش", "🧃", 0.95f);

    companion object {
        fun fromName(name: String?): DrinkType =
            entries.firstOrNull { it.name == name } ?: WATER
    }
}

/** Effective hydration (ml) a drink of [amountMl] of [type] contributes to the goal. */
fun effectiveMlFor(amountMl: Int, type: DrinkType): Int =
    (amountMl * type.hydrationFactor).roundToInt()
