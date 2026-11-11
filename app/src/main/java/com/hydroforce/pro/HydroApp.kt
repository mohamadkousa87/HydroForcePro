package com.hydroforce.pro

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

class HydroApp : Application() {

    companion object {
        const val CHANNEL_ALARM = "hydro_alarm"
        const val CHANNEL_MISSED = "hydro_missed"
        /** Dismissible mirror of the ringing alarm; ongoing FGS notifications aren't relayed to watches. */
        const val CHANNEL_WATCH = "hydro_watch"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            "تنبيهات شرب الماء",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيه بملء الشاشة يتطلب التوثيق بالكاميرا"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val missed = NotificationChannel(
            CHANNEL_MISSED,
            "التنبيهات الفائتة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعار عند تجاهل تنبيه شرب الماء"
        }
        // Mirror channel: HIGH so it reaches paired watches, but silent on the phone
        // (the foreground service already plays sound/vibration on the phone).
        val watch = NotificationChannel(
            CHANNEL_WATCH,
            "تنبيه الساعة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعار قابل للإزالة يصل الساعة الذكية مع رنين التنبيه"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(alarm, missed, watch))
    }
}
