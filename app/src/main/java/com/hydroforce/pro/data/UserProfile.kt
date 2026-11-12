package com.hydroforce.pro.data

enum class Gender(val label: String, val labelEn: String) {
    MALE("ذكر", "Male"),
    FEMALE("أنثى", "Female");

    fun label(lang: Language): String = if (lang == Language.EN) labelEn else label
}

enum class ActivityLevel(
    val label: String,
    val labelEn: String,
    val description: String,
    val descriptionEn: String,
    val bonusMl: Int
) {
    SEDENTARY("خامل", "Sedentary", "عمل مكتبي وحركة قليلة", "Desk work, little movement", 0),
    LIGHT("خفيف", "Light", "مشي أو تمارين خفيفة 1-3 أيام", "Walking or light exercise 1-3 days", 150),
    MODERATE("متوسط", "Moderate", "تمارين متوسطة 3-5 أيام", "Moderate exercise 3-5 days", 300),
    HIGH("عالٍ / جيم", "High / Gym", "تمارين شاقة أو جيم يومي", "Hard training or daily gym", 500);

    fun label(lang: Language): String = if (lang == Language.EN) labelEn else label
    fun description(lang: Language): String = if (lang == Language.EN) descriptionEn else description
}

/** Times are stored as minutes-of-day (0..1439). */
data class UserProfile(
    val gender: Gender = Gender.MALE,
    val age: Int = 30,
    val weightKg: Float = 70f,
    val heightCm: Int = 170,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val cupSizeMl: Int = 400,
    val wakeMinutes: Int = 7 * 60,
    val sleepMinutes: Int = 23 * 60,
    val workModeEnabled: Boolean = true,
    val workStartMinutes: Int = 8 * 60 + 30,
    val workEndMinutes: Int = 17 * 60 + 30,
    /** URI of the chosen alarm tone; empty = system default alarm tone. Used in both ring modes. */
    val ringtoneUri: String = "",
    val vibrationEnabled: Boolean = true
)

data class AlarmState(
    val nextAlarmAt: Long = 0L,
    val snoozeUsed: Int = 0,
    val lastDrinkAt: Long = 0L
)

val CUP_SIZES = listOf(200, 250, 330, 400, 500)

/** Reminders are never scheduled closer together than this (gentler on digestion). */
const val MIN_INTERVAL_MINUTES = 120

/** Snooze durations (minutes) the user can pick at the alarm. */
val SNOOZE_OPTIONS = listOf(5, 10)

/** Max snoozes allowed per alarm cycle (softened strictness). */
const val MAX_SNOOZES = 3

/** Minutes to re-remind after an unanswered alarm times out. */
const val RETRY_MINUTES = 10

/** Minutes to postpone when the alarm fires while the user is on a phone/VoIP call. */
const val CALL_POSTPONE_MINUTES = 5
