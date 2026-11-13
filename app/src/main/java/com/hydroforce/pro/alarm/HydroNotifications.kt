package com.hydroforce.pro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.hydroforce.pro.HydroApp
import com.hydroforce.pro.R
import com.hydroforce.pro.data.HydrationTips
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.ui.tr

/** Notifications posted from the receiver (workout sip pulse + daily tip) — no service needed. */
object HydroNotifications {

    private const val WATCH_SIP_ID = 60
    private const val DAILY_TIP_ID = 61

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** Double buzz + short tone, then a watch-mirrored sip notification. */
    fun workoutSip(context: Context, lang: Language) {
        runCatching {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            vibrator(context).vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), intArrayOf(0, 200, 0, 200), -1),
                attrs
            )
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        }
        val text = tr(lang, "رشفة التمرين 🏋️: اشرب 3 رشفات الآن (~120 مل)", "Workout sip 🏋️: take 3 sips now (~120 ml)")
        val n = NotificationCompat.Builder(context, HydroApp.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(tr(lang, "وقت رشفة الماء 💧", "Water sip time 💧"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java).notify(WATCH_SIP_ID, n)
    }

    /** Quiet once-a-day medical tip from the bundled tips. */
    fun dailyTip(context: Context, lang: Language) {
        val tip = HydrationTips.tipOfTheDay()
        val body = "${tip.emoji} ${tip.title(lang)}\n${tip.body(lang)}"
        val n = NotificationCompat.Builder(context, HydroApp.CHANNEL_DAILY_TIPS)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(tr(lang, "💡 معلومة الترطيب اليوم", "💡 Today's hydration tip"))
            .setContentText(tip.title(lang))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java).notify(DAILY_TIP_ID, n)
    }
}
