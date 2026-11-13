package com.hydroforce.pro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules the three independent alarm paths. Each uses its own requestCode + action so they never
 * cancel or trigger one another:
 *  - MAIN   (100): fires the full-screen enforcer AlarmService.
 *  - WORKOUT(200): gym sip pulse, handled in the receiver (no lock screen).
 *  - TIP    (300): the quiet daily medical tip, handled in the receiver.
 */
object AlarmScheduler {

    const val REQ_MAIN_ALARM = 100
    const val REQ_WORKOUT_SIP = 200
    const val REQ_DAILY_TIP = 300

    const val ACTION_MAIN_ALARM = "com.hydroforce.pro.action.MAIN_ALARM"
    const val ACTION_WORKOUT_SIP = "com.hydroforce.pro.action.WORKOUT_SIP"
    const val ACTION_DAILY_TIP = "com.hydroforce.pro.action.DAILY_TIP"

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun scheduleAt(context: Context, triggerAt: Long, action: String, requestCode: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, action, requestCode)
        am.cancel(pi)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelPath(context: Context, action: String, requestCode: Int) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, action, requestCode))
    }

    // ----- Main enforcer alarm (unchanged public API) -----
    fun schedule(context: Context, triggerAt: Long) =
        scheduleAt(context, triggerAt, ACTION_MAIN_ALARM, REQ_MAIN_ALARM)

    fun cancel(context: Context) =
        cancelPath(context, ACTION_MAIN_ALARM, REQ_MAIN_ALARM)

    // ----- Workout sip pulses -----
    fun scheduleWorkoutSip(context: Context, triggerAt: Long) =
        scheduleAt(context, triggerAt, ACTION_WORKOUT_SIP, REQ_WORKOUT_SIP)

    fun cancelWorkoutSip(context: Context) =
        cancelPath(context, ACTION_WORKOUT_SIP, REQ_WORKOUT_SIP)

    // ----- Daily medical tip -----
    fun scheduleDailyTip(context: Context, triggerAt: Long) =
        scheduleAt(context, triggerAt, ACTION_DAILY_TIP, REQ_DAILY_TIP)

    fun cancelDailyTip(context: Context) =
        cancelPath(context, ACTION_DAILY_TIP, REQ_DAILY_TIP)
}
