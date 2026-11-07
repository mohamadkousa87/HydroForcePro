package com.hydroforce.pro.data

enum class Gender(val label: String) {
    MALE("ذكر"),
    FEMALE("أنثى")
}

enum class ActivityLevel(val label: String, val description: String, val bonusMl: Int) {
    SEDENTARY("خامل", "عمل مكتبي وحركة قليلة", 0),
    LIGHT("خفيف", "مشي أو تمارين خفيفة 1-3 أيام", 300),
    MODERATE("متوسط", "تمارين متوسطة 3-5 أيام", 500),
    HIGH("عالٍ / جيم", "تمارين شاقة أو جيم يومي", 800)
}

/** Times are stored as minutes-of-day (0..1439). */
data class UserProfile(
    val gender: Gender = Gender.MALE,
    val age: Int = 30,
    val weightKg: Float = 70f,
    val heightCm: Int = 170,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val cupSizeMl: Int = 250,
    val wakeMinutes: Int = 7 * 60,
    val sleepMinutes: Int = 23 * 60,
    val workModeEnabled: Boolean = true,
    val workStartMinutes: Int = 8 * 60 + 30,
    val workEndMinutes: Int = 17 * 60 + 30
)

data class AlarmState(
    val nextAlarmAt: Long = 0L,
    val snoozeUsed: Int = 0,
    val lastDrinkAt: Long = 0L
)

val CUP_SIZES = listOf(200, 250, 330, 500)

/** Snooze durations (minutes) the user can pick at the alarm. */
val SNOOZE_OPTIONS = listOf(5, 10)

/** Max snoozes allowed per alarm cycle (softened strictness). */
const val MAX_SNOOZES = 3

/** Minutes to re-remind after an unanswered alarm times out. */
const val RETRY_MINUTES = 10

/** Minutes to postpone when the alarm fires while the user is on a phone/VoIP call. */
const val CALL_POSTPONE_MINUTES = 5
