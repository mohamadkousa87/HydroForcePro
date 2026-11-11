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
import android.net.Uri
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
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.Meeting
import com.hydroforce.pro.data.RETRY_MINUTES
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.data.UserProfile
import com.hydroforce.pro.data.covering
import com.hydroforce.pro.ui.tr
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

    private var lang: Language = Language.AR
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
            val repo = SettingsRepository(applicationContext)
            lang = repo.currentLanguage()
            // If the user is on a phone/VoIP call, don't ring — postpone and leave them in peace.
            if (isBusyOnCall()) {
                HydrationManager.schedulePostpone(applicationContext)
                notifyPostponedForCall()
                stopEverything()
                return@launch
            }
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
            if (isDiscreet) playDiscreet(profile) else playEnforcer(profile)
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
            tr(lang, "وضع العمل الهادئ 💼 — وجّه الكاميرا نحو كأس الماء لإيقاف التنبيه", "Quiet work mode 💼 — point the camera at your water to stop the alarm")
        } else {
            tr(lang, "وضع المُلزِم الكامل 🔔 — لن يتوقف التنبيه إلا بتوثيق الكأس بالكاميرا", "Full enforcer mode 🔔 — the alarm stops only when you verify your drink by camera")
        }
        return NotificationCompat.Builder(this, HydroApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(tr(lang, "حان وقت شرب الماء 💧", "Time to drink water 💧"))
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

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** The user-selected tone, shared by both ring modes; falls back to the system default alarm. */
    private fun selectedUri(profile: UserProfile): Uri? =
        profile.ringtoneUri.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: defaultAlarmUri()

    private fun startPlayer(uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
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
            true
        } catch (_: Exception) {
            runCatching { player?.release() }
            player = null
            false
        }
    }

    private fun playEnforcer(profile: UserProfile) {
        // Try the chosen tone; if it can't be played (e.g. a revoked file URI), use the default alarm.
        if (!startPlayer(selectedUri(profile))) {
            startPlayer(defaultAlarmUri())
        }
        if (profile.vibrationEnabled) {
            vibrate(
                longArrayOf(0, 900, 400, 900, 400),
                intArrayOf(0, 255, 0, 255, 0),
                AudioAttributes.USAGE_ALARM
            )
        }
    }

    private fun playDiscreet(profile: UserProfile) {
        val uri = selectedUri(profile)
        chime = runCatching { uri?.let { RingtoneManager.getRingtone(this, it) } }.getOrNull()?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            runCatching { play() }
        }
        if (chime == null) {
            chime = runCatching {
                RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }.getOrNull()?.apply { runCatching { play() } }
        }
        if (profile.vibrationEnabled) {
            vibrate(
                longArrayOf(0, 250, 200, 250, 6000),
                intArrayOf(0, 120, 0, 120, 0),
                AudioAttributes.USAGE_NOTIFICATION
            )
        }
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
            .setContentTitle(tr(lang, "تم تأجيل تنبيه الماء 📞", "Water reminder postponed 📞"))
            .setContentText(tr(lang, "أنت في مكالمة الآن — سيعاد التنبيه بعد $CALL_POSTPONE_MINUTES دقائق.", "You're on a call — reminder returns in $CALL_POSTPONE_MINUTES min."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(alarmIntent(_discreet.value))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTIFICATION_ID, note)
    }

    private fun notifyPostponedForMeeting(meeting: Meeting) {
        val label = meeting.title.ifBlank { tr(lang, "اجتماعك", "your meeting") }
        val note = NotificationCompat.Builder(this, HydroApp.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(tr(lang, "تم تأجيل تنبيه الماء 📅", "Water reminder postponed 📅"))
            .setContentText(tr(lang, "أنت في «$label» الآن — سيعاد التنبيه بعد انتهائه.", "You're in \"$label\" now — reminder returns after it ends."))
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
                .setContentTitle(tr(lang, "فاتك تنبيه شرب الماء ⚠️", "You missed a water reminder ⚠️"))
                .setContentText(tr(lang, "سيعاد التنبيه بعد $RETRY_MINUTES دقائق. اضغط لتوثيق الشرب الآن بالكاميرا.", "Reminder returns in $RETRY_MINUTES min. Tap to verify drinking now by camera."))
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
