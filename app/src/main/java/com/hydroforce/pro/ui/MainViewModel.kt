package com.hydroforce.pro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydroforce.pro.alarm.AlarmService
import com.hydroforce.pro.alarm.HydrationManager
import com.hydroforce.pro.data.AlarmState
import com.hydroforce.pro.data.AppDatabase
import com.hydroforce.pro.data.HydrationCalculator
import com.hydroforce.pro.data.HydrationResult
import com.hydroforce.pro.data.CsvExporter
import com.hydroforce.pro.data.DrinkType
import com.hydroforce.pro.data.IntakeLog
import com.hydroforce.pro.data.Meeting
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.StatsCalculator
import com.hydroforce.pro.data.StatsData
import com.hydroforce.pro.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val dao = AppDatabase.get(app).intakeDao()

    /** null while loading from DataStore. */
    val onboarded: StateFlow<Boolean?> = repo.onboarded
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profile: StateFlow<UserProfile> = repo.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())

    val alarmState: StateFlow<AlarmState> = repo.alarmState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlarmState())

    val result: StateFlow<HydrationResult> = combine(profile, repo.language) { p, lang ->
        HydrationCalculator.calculate(p, lang)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HydrationCalculator.calculate(UserProfile()))

    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    /** Daily progress resets at the user's wake-up time (hydration cycle), not midnight. */
    private val dayStart = combine(now, profile) { t, p ->
        ScheduleMath.currentHydrationCycleStart(t, p.wakeMinutes)
    }.distinctUntilChanged()

    val todayLogs: StateFlow<List<IntakeLog>> = dayStart
        .flatMapLatest { dao.logsSince(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ringing: StateFlow<Boolean> = AlarmService.ringing

    val meetings: StateFlow<List<Meeting>> = repo.meetings
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addMeeting(startMillis: Long, endMillis: Long, title: String) {
        viewModelScope.launch {
            repo.addMeeting(startMillis, endMillis, title)
            if (!AlarmService.ringing.value) HydrationManager.rescheduleFrom(getApplication())
        }
    }

    fun removeMeeting(id: Long) {
        viewModelScope.launch {
            repo.removeMeeting(id)
            if (!AlarmService.ringing.value) HydrationManager.rescheduleFrom(getApplication())
        }
    }

    val stats: StateFlow<StatsData?> = combine(dao.allLogsFlow(), profile) { logs, p ->
        StatsCalculator.compute(logs, p, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Writes the full intake log to a CSV file and returns it for sharing. */
    fun exportCsv(onReady: (File?) -> Unit) {
        viewModelScope.launch {
            val file = runCatching {
                CsvExporter.write(getApplication(), dao.allLogs())
            }.getOrNull()
            onReady(file)
        }
    }

    init {
        viewModelScope.launch { ensureScheduled() }
    }

    /** Called on start/resume: keeps an alarm armed (e.g. after exact-alarm permission is granted). */
    fun ensureScheduledAsync() {
        viewModelScope.launch { ensureScheduled() }
    }

    private suspend fun ensureScheduled() {
        if (!repo.isOnboarded() || AlarmService.ringing.value) return
        HydrationManager.restore(getApplication())
    }

    fun saveProfile(p: UserProfile, onSaved: () -> Unit) {
        viewModelScope.launch {
            repo.saveProfile(p)
            // Preserve the running countdown instead of resetting it on every save.
            HydrationManager.rescheduleKeepingCountdown(getApplication())
            onSaved()
        }
    }

    private val _pendingDrinkType = kotlinx.coroutines.flow.MutableStateFlow(DrinkType.WATER)
    val pendingDrinkType: StateFlow<DrinkType> = _pendingDrinkType

    fun setDrinkType(type: DrinkType) {
        _pendingDrinkType.value = type
    }

    /**
     * Proactive "I drank now". [amountMl] is the chosen volume; [viaPromise] = user used the
     * emergency failsafe (AI couldn't detect the cup), recorded with SOURCE_PROMISE.
     */
    fun onManualDrinkVerified(amountMl: Int, viaPromise: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val source = if (viaPromise) HydrationManager.SOURCE_PROMISE else HydrationManager.SOURCE_MANUAL
            HydrationManager.recordVerifiedDrink(getApplication(), amountMl, source, _pendingDrinkType.value)
            AlarmService.stop(getApplication())
            _pendingDrinkType.value = DrinkType.WATER
            onDone()
        }
    }
}
