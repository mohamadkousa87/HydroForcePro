package com.hydroforce.pro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hydroforce_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val GENDER = stringPreferencesKey("gender")
        val AGE = intPreferencesKey("age")
        val WEIGHT = floatPreferencesKey("weight")
        val HEIGHT = intPreferencesKey("height")
        val ACTIVITY = stringPreferencesKey("activity")
        val CUP = intPreferencesKey("cup")
        val WAKE = intPreferencesKey("wake")
        val SLEEP = intPreferencesKey("sleep")
        val WORK_ENABLED = booleanPreferencesKey("work_enabled")
        val WORK_START = intPreferencesKey("work_start")
        val WORK_END = intPreferencesKey("work_end")
        val NEXT_ALARM = longPreferencesKey("next_alarm")
        val SNOOZE_USED = intPreferencesKey("snooze_used")
        val LAST_DRINK = longPreferencesKey("last_drink")
        val MEETINGS = stringPreferencesKey("meetings")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val RINGTONE = stringPreferencesKey("ringtone_uri")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val MEALS_ENABLED = booleanPreferencesKey("meals_enabled")
        val BREAKFAST = intPreferencesKey("breakfast_min")
        val LUNCH = intPreferencesKey("lunch_min")
        val DINNER = intPreferencesKey("dinner_min")
        val BREAKFAST_ON = booleanPreferencesKey("breakfast_on")
        val LUNCH_ON = booleanPreferencesKey("lunch_on")
        val DINNER_ON = booleanPreferencesKey("dinner_on")
        val FASTING_MODE = stringPreferencesKey("fasting_mode")
        val FASTING_RECURRENCE = stringPreferencesKey("fasting_recurrence")
        val FAST_START = intPreferencesKey("fast_start")
        val FAST_END = intPreferencesKey("fast_end")
        val SUHOOR = booleanPreferencesKey("suhoor_reminder")
        val DAILY_TIPS = booleanPreferencesKey("daily_tips_enabled")
        // Transient cycle state (not part of the medical profile)
        val WORKOUT_END = longPreferencesKey("workout_end")
        val WORKOUT_BONUS_ML = intPreferencesKey("workout_bonus_ml")
        val WORKOUT_BONUS_CYCLE = longPreferencesKey("workout_bonus_cycle")
        val FASTING_ACTIVATED_CYCLE = longPreferencesKey("fasting_activated_cycle")
    }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val language: Flow<Language> = store.data.map { prefs ->
        prefs[Keys.LANGUAGE]?.let { runCatching { Language.valueOf(it) }.getOrNull() } ?: Language.AR
    }

    suspend fun currentLanguage(): Language = language.first()

    suspend fun setLanguage(lang: Language) {
        store.edit { it[Keys.LANGUAGE] = lang.name }
    }

    val onboarded: Flow<Boolean> = store.data.map { it[Keys.ONBOARDED] ?: false }

    val profile: Flow<UserProfile> = store.data.map { it.toProfile() }

    val alarmState: Flow<AlarmState> = store.data.map {
        AlarmState(
            nextAlarmAt = it[Keys.NEXT_ALARM] ?: 0L,
            snoozeUsed = it[Keys.SNOOZE_USED] ?: 0,
            lastDrinkAt = it[Keys.LAST_DRINK] ?: 0L
        )
    }

    /** Upcoming/active meetings only (past ones are filtered out). */
    val meetings: Flow<List<Meeting>> = store.data.map { prefs ->
        val now = System.currentTimeMillis()
        MeetingCodec.decode(prefs[Keys.MEETINGS]).filter { it.endMillis > now }.sortedBy { it.startMillis }
    }

    suspend fun currentProfile(): UserProfile = profile.first()
    suspend fun currentAlarmState(): AlarmState = alarmState.first()
    suspend fun currentMeetings(): List<Meeting> = meetings.first()
    suspend fun isOnboarded(): Boolean = onboarded.first()

    suspend fun addMeeting(start: Long, end: Long, title: String) {
        store.edit { prefs ->
            val now = System.currentTimeMillis()
            val existing = MeetingCodec.decode(prefs[Keys.MEETINGS]).filter { it.endMillis > now }
            val meeting = Meeting(id = System.currentTimeMillis(), startMillis = start, endMillis = end, title = title)
            prefs[Keys.MEETINGS] = MeetingCodec.encode(existing + meeting)
        }
    }

    suspend fun removeMeeting(id: Long) {
        store.edit { prefs ->
            val now = System.currentTimeMillis()
            val kept = MeetingCodec.decode(prefs[Keys.MEETINGS]).filter { it.endMillis > now && it.id != id }
            prefs[Keys.MEETINGS] = MeetingCodec.encode(kept)
        }
    }

    suspend fun saveProfile(p: UserProfile) {
        store.edit {
            it[Keys.ONBOARDED] = true
            it[Keys.GENDER] = p.gender.name
            it[Keys.AGE] = p.age
            it[Keys.WEIGHT] = p.weightKg
            it[Keys.HEIGHT] = p.heightCm
            it[Keys.ACTIVITY] = p.activity.name
            it[Keys.CUP] = p.cupSizeMl
            it[Keys.WAKE] = p.wakeMinutes
            it[Keys.SLEEP] = p.sleepMinutes
            it[Keys.WORK_ENABLED] = p.workModeEnabled
            it[Keys.WORK_START] = p.workStartMinutes
            it[Keys.WORK_END] = p.workEndMinutes
            it[Keys.RINGTONE] = p.ringtoneUri
            it[Keys.VIBRATION] = p.vibrationEnabled
            it[Keys.MEALS_ENABLED] = p.mealsEnabled
            it[Keys.BREAKFAST] = p.breakfastMinutes
            it[Keys.LUNCH] = p.lunchMinutes
            it[Keys.DINNER] = p.dinnerMinutes
            it[Keys.BREAKFAST_ON] = p.breakfastOn
            it[Keys.LUNCH_ON] = p.lunchOn
            it[Keys.DINNER_ON] = p.dinnerOn
            it[Keys.FASTING_MODE] = p.fastingMode.name
            it[Keys.FASTING_RECURRENCE] = p.fastingRecurrence.name
            it[Keys.FAST_START] = p.fastStartMinutes
            it[Keys.FAST_END] = p.fastEndMinutes
            it[Keys.SUHOOR] = p.suhoorReminder
            it[Keys.DAILY_TIPS] = p.dailyTipsEnabled
        }
    }

    suspend fun setFastingMode(mode: FastingMode) {
        store.edit { it[Keys.FASTING_MODE] = mode.name }
    }

    // ----- Transient cycle state -----
    val workoutEnd: Flow<Long> = store.data.map { it[Keys.WORKOUT_END] ?: 0L }
    suspend fun currentWorkoutEnd(): Long = workoutEnd.first()
    suspend fun setWorkoutEnd(at: Long) { store.edit { it[Keys.WORKOUT_END] = at } }

    /** (bonusMl, cycleStart) for the workout sweat bonus, as a stream for the UI. */
    val workoutBonusRaw: Flow<Pair<Int, Long>> = store.data.map {
        (it[Keys.WORKOUT_BONUS_ML] ?: 0) to (it[Keys.WORKOUT_BONUS_CYCLE] ?: 0L)
    }

    /** Workout sweat bonus, valid only for the cycle it was granted in. */
    suspend fun workoutBonusFor(cycleStart: Long): Int {
        val prefs = store.data.first()
        return if ((prefs[Keys.WORKOUT_BONUS_CYCLE] ?: 0L) == cycleStart) prefs[Keys.WORKOUT_BONUS_ML] ?: 0 else 0
    }
    suspend fun setWorkoutBonus(ml: Int, cycleStart: Long) {
        store.edit {
            it[Keys.WORKOUT_BONUS_ML] = ml
            it[Keys.WORKOUT_BONUS_CYCLE] = cycleStart
        }
    }

    suspend fun fastingActivatedCycle(): Long = store.data.first()[Keys.FASTING_ACTIVATED_CYCLE] ?: 0L
    suspend fun setFastingActivatedCycle(cycleStart: Long) {
        store.edit { it[Keys.FASTING_ACTIVATED_CYCLE] = cycleStart }
    }

    suspend fun setNextAlarm(at: Long) {
        store.edit { it[Keys.NEXT_ALARM] = at }
    }

    suspend fun setSnoozeUsed(count: Int) {
        store.edit { it[Keys.SNOOZE_USED] = count }
    }

    suspend fun setLastDrink(at: Long) {
        store.edit { it[Keys.LAST_DRINK] = at }
    }

    private fun Preferences.toProfile(): UserProfile {
        val d = UserProfile()
        return UserProfile(
            gender = this[Keys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: d.gender,
            age = this[Keys.AGE] ?: d.age,
            weightKg = this[Keys.WEIGHT] ?: d.weightKg,
            heightCm = this[Keys.HEIGHT] ?: d.heightCm,
            activity = this[Keys.ACTIVITY]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: d.activity,
            cupSizeMl = this[Keys.CUP] ?: d.cupSizeMl,
            wakeMinutes = this[Keys.WAKE] ?: d.wakeMinutes,
            sleepMinutes = this[Keys.SLEEP] ?: d.sleepMinutes,
            workModeEnabled = this[Keys.WORK_ENABLED] ?: d.workModeEnabled,
            workStartMinutes = this[Keys.WORK_START] ?: d.workStartMinutes,
            workEndMinutes = this[Keys.WORK_END] ?: d.workEndMinutes,
            ringtoneUri = this[Keys.RINGTONE] ?: d.ringtoneUri,
            vibrationEnabled = this[Keys.VIBRATION] ?: d.vibrationEnabled,
            mealsEnabled = this[Keys.MEALS_ENABLED] ?: d.mealsEnabled,
            breakfastMinutes = this[Keys.BREAKFAST] ?: d.breakfastMinutes,
            lunchMinutes = this[Keys.LUNCH] ?: d.lunchMinutes,
            dinnerMinutes = this[Keys.DINNER] ?: d.dinnerMinutes,
            breakfastOn = this[Keys.BREAKFAST_ON] ?: d.breakfastOn,
            lunchOn = this[Keys.LUNCH_ON] ?: d.lunchOn,
            dinnerOn = this[Keys.DINNER_ON] ?: d.dinnerOn,
            fastingMode = this[Keys.FASTING_MODE]?.let { runCatching { FastingMode.valueOf(it) }.getOrNull() } ?: d.fastingMode,
            fastingRecurrence = this[Keys.FASTING_RECURRENCE]?.let { runCatching { FastingRecurrence.valueOf(it) }.getOrNull() } ?: d.fastingRecurrence,
            fastStartMinutes = this[Keys.FAST_START] ?: d.fastStartMinutes,
            fastEndMinutes = this[Keys.FAST_END] ?: d.fastEndMinutes,
            suhoorReminder = this[Keys.SUHOOR] ?: d.suhoorReminder,
            dailyTipsEnabled = this[Keys.DAILY_TIPS] ?: d.dailyTipsEnabled
        )
    }
}
