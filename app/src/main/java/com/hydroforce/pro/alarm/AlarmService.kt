package com.hydroforce.pro.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hydroforce.pro.HydroApp
import com.hydroforce.pro.R
import com.hydroforce.pro.data.CALL_POSTPONE_MINUTES
import com.hydroforce.pro.data.Meeting
import com.hydroforce.pro.data.RETRY_MINUTES
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.covering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that rings until the user proves drinking with the camera (or snoozes).
 * Work hours -> "Discreet Work Mode" (gentle vibration + single chime, mutable).
 * Otherwise -> "Full Enforcer Mode" (looping alarm sound + strong vibration).
 */
class AlarmService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.hydroforce.pro.action.START"
        const val ACTION_MUTE = "com.hydroforce.pro.action.MUTE"
        const val ACTION_STOP = "com.hydroforce.pro.action.STOP"
        const val EXTRA_DISCREET = "discreet"

        private const val NOTIFICATION_ID = 42
        private const val MISSED_NOTIFICATION_ID = 43
        private const val CALL_NOTIFICATION_ID = 44
        private const val MEETING_NOTIFICATION_ID = 45
        private const val RING_TIMEOUT_MS = 10L * 60 * 1000

        private val _ringing = MutableStateFlow(false)
        val ringing: StateFlow<Boolean> = _ringing.asStateFlow()
        private val _muted = MutableStateFlow(false)
        val muted: StateFlow<Boolean> = _muted.asStateFlow()
        private val _discreet = MutableStateFlow(false)
        val discreet: StateFlow<Boolean> = _discreet.asStateFlow()

        fun mute(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_MUTE))
        }

        fun stop(context: Context) {
            if (_ringing.value) {
                context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
            }
            context.getSystemService(NotificationManager::class.java).cancel(MISSED_NOTIFICATION_ID)
        }
    }

    private var player: MediaPlayer? = null
    private var chime: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onRingTimeout() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRinging()
            ACTION_MUTE -> {
                stopSound()
                _muted.value = true
            }
            ACTION_STOP -> stopEverything()
            else -> if (!_ringing.value) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        // Promote to foreground immediately with a provisional mode, then refine once the profile loads.
        val provisional = buildNotification(discreet = _discreet.value)
        goForeground(provisional)
        lifecycleScope.launch {
            // If the user is on a phone/VoIP call, don't ring — postpone and leave them in peace.
            if (isBusyOnCall()) {
                HydrationManager.schedulePostpone(applicationContext)
                notifyPostponedForCall()
                stopEverything()
                return@launch
            }
            val repo = SettingsRepository(applicationContext)
            // If a scheduled meeting is in progress, postpone until it ends instead of ringing.
            val meeting = repo.currentMeetings().covering(System.currentTimeMillis())
            if (meeting != null) {
                HydrationManager.schedulePostponeUntil(applicationContext, meeting.endMillis)
                notifyPostponedForMeeting(meeting)
                stopEverything()
                return@launch
            }
            _ringing.value = true
            _muted.value = false
            acquireWakeLock()
            val profile = repo.currentProfile()
            val isDiscreet = ScheduleMath.isWorkTime(System.currentTimeMillis(), profile)
            _discreet.value = isDiscreet
            HydrationManager.markAlarmRinging(applicationContext)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(isDiscreet))
            stopSound()
            if (isDiscreet) playDiscreet() else playEnforcer()
            tryLaunchAlarmScreen(isDiscreet)
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, RING_TIMEOUT_MS)
        }
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun alarmIntent(discreet: Boolean): PendingIntent {
        val intent = Intent(this, AlarmVerifyActivity::class.java)
            .putExtra(EXTRA_DISCREET, discreet)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(
            this, 2001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(discreet: Boolean): Notification {
        val pi = alarmIntent(discreet)
        val text = if (discreet) {
            "وضع العمل الهادئ 💼 — وجّه الكاميرا نحو كأس الماء لإيقاف التنبيه"
        } else {
            "وضع المُلزِم الكامل 🔔 — لن يتوقف التنبيه إلا بتوثيق الكأس بالكاميرا"
        }
        return NotificationCompat.Builder(this, HydroApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("حان وقت شرب الماء 💧")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun tryLaunchAlarmScreen(discreet: Boolean) {
        try {
            startActivity(
                Intent(this, AlarmVerifyActivity::class.java)
                    .putExtra(EXTRA_DISCREET, discreet)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Background activity starts may be blocked; the full-screen intent covers that case.
        }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray, amplitudes: IntArray, usage: Int) {
        val attrs = AudioAttributes.Builder().setUsage(usage).build()
        vibrator().vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0), attrs)
    }

    private fun playEnforcer() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
        vibrate(
            longArrayOf(0, 900, 400, 900, 400),
            intArrayOf(0, 255, 0, 255, 0),
            AudioAttributes.USAGE_ALARM
        )
    }

    private fun playDiscreet() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        chime = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
        vibrate(
            longArrayOf(0, 250, 200, 250, 6000),
            intArrayOf(0, 120, 0, 120, 0),
            AudioAttributes.USAGE_NOTIFICATION
        )
    }

    private fun stopSound() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        chime?.stop()
        chime = null
        vibrator().cancel()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HydroForce:alarm")
            .apply { acquire(RING_TIMEOUT_MS + 60_000L) }
    }

    /** True when a cellular or VoIP call is active or ringing (no permission needed). */
    private fun isBusyOnCall(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return false
        return when (am.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_RINGTONE -> true
            else -> false
        }
    }

    private fun notifyPostponedForCall() {
        val note = NotificationCompat.Builder(this, HydroApp.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("تم تأجيل تنبيه الماء 📞")
            .setContentText("أنت في مكالمة الآن — سيعاد التنبيه بعد $CALL_POSTPONE_MINUTES دقائق.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(alarmIntent(_discreet.value))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTIFICATION_ID, note)
    }

    private fun notifyPostponedForMeeting(meeting: Meeting) {
        val label = meeting.title.ifBlank { "اجتماعك" }
        val note = NotificationCompat.Builder(this, HydroApp.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("تم تأجيل تنبيه الماء 📅")
            .setContentText("أنت في «$label» الآن — سيعاد التنبيه بعد انتهائه.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(alarmIntent(_discreet.value))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(MEETING_NOTIFICATION_ID, note)
    }

    private fun onRingTimeout() {
        lifecycleScope.launch {
            HydrationManager.scheduleRetry(applicationContext)
            val missed = NotificationCompat.Builder(this@AlarmService, HydroApp.CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_stat_drop)
                .setContentTitle("فاتك تنبيه شرب الماء ⚠️")
                .setContentText("سيعاد التنبيه بعد $RETRY_MINUTES دقائق. اضغط لتوثيق الشرب الآن بالكاميرا.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(alarmIntent(_discreet.value))
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(MISSED_NOTIFICATION_ID, missed)
            stopEverything()
        }
    }

    private fun stopEverything() {
        handler.removeCallbacks(timeoutRunnable)
        stopSound()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        _ringing.value = false
        _muted.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        stopSound()
        wakeLock?.let { if (it.isHeld) it.release() }
        _ringing.value = false
        super.onDestroy()
    }
}
