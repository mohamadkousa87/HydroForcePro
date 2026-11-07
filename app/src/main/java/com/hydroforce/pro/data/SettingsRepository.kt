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
    }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
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
        }
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
            workEndMinutes = this[Keys.WORK_END] ?: d.workEndMinutes
        )
    }
}
