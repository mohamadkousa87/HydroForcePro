package com.hydroforce.pro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hydroforce.pro.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_MAIN_ALARM -> {
                // Full-screen enforcer: start the foreground ringing service (unchanged behavior).
                val serviceIntent = Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            AlarmScheduler.ACTION_WORKOUT_SIP -> {
                val app = context.applicationContext
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        if (HydrationManager.onWorkoutSipAlarm(app)) {
                            val lang = SettingsRepository(app).currentLanguage()
                            HydroNotifications.workoutSip(app, lang)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            AlarmScheduler.ACTION_DAILY_TIP -> {
                val app = context.applicationContext
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val lang = SettingsRepository(app).currentLanguage()
                        HydroNotifications.dailyTip(app, lang)
                        HydrationManager.scheduleNextDailyTip(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                HydrationManager.restore(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
