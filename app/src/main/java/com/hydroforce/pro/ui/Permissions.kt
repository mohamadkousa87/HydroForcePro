package com.hydroforce.pro.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hydroforce.pro.alarm.AlarmScheduler
import com.hydroforce.pro.data.Language

enum class PermKey { CAMERA, NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN, BATTERY }

data class PermItem(val key: PermKey, val title: String, val description: String, val granted: Boolean)

object PermissionChecker {

    fun status(context: Context, lang: Language = Language.AR): List<PermItem> {
        fun t(ar: String, en: String) = tr(lang, ar, en)
        val list = mutableListOf<PermItem>()
        list += PermItem(
            PermKey.CAMERA, t("الكاميرا", "Camera"), t("لتوثيق كأس الماء بالتحقق الذكي", "To verify your drink with smart detection"),
            granted(context, Manifest.permission.CAMERA)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += PermItem(
                PermKey.NOTIFICATIONS, t("الإشعارات", "Notifications"), t("لإظهار تنبيه شرب الماء", "To show the drink reminder"),
                granted(context, Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += PermItem(
                PermKey.EXACT_ALARM, t("المنبهات الدقيقة", "Exact alarms"), t("ليرن التنبيه في موعده بالضبط", "So the reminder fires exactly on time"),
                AlarmScheduler.canScheduleExact(context)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list += PermItem(
                PermKey.FULL_SCREEN, t("التنبيه بملء الشاشة", "Full-screen alert"), t("لعرض شاشة التنبيه فوق شاشة القفل", "To show the alarm over the lock screen"),
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            )
        }
        list += PermItem(
            PermKey.BATTERY, t("تجاهل تحسين البطارية", "Ignore battery optimization"), t("حتى لا يوقف النظام التنبيهات", "So the system doesn't stop reminders"),
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
        return list
    }

    private fun granted(context: Context, perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("BatteryLife")
    fun settingsIntent(context: Context, key: PermKey): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (key) {
            PermKey.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            } else null
            PermKey.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else null
            PermKey.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            PermKey.CAMERA, PermKey.NOTIFICATIONS ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
    }
}

/** Remembers permission status and refreshes it every time the screen resumes. */
@Composable
fun rememberPermissionStatus(onResume: () -> Unit = {}): List<PermItem> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val lang = LocalLanguage.current
    var items by remember(lang) { mutableStateOf(PermissionChecker.status(context, lang)) }
    DisposableEffect(owner, lang) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                items = PermissionChecker.status(context, lang)
                onResume()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return items
}

@Composable
fun PermissionsHealthCard(items: List<PermItem>, onChanged: () -> Unit) {
    val context = LocalContext.current
    val missing = items.filter { !it.granted }
    if (missing.isEmpty()) return

    var askedRuntime by remember { mutableStateOf(setOf<PermKey>()) }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onChanged() }

    fun request(item: PermItem) {
        when (item.key) {
            PermKey.CAMERA, PermKey.NOTIFICATIONS -> {
                if (item.key in askedRuntime) {
                    PermissionChecker.settingsIntent(context, item.key)?.let {
                        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    askedRuntime = askedRuntime + item.key
                    val perm = if (item.key == PermKey.CAMERA) {
                        Manifest.permission.CAMERA
                    } else {
                        Manifest.permission.POST_NOTIFICATIONS
                    }
                    runtimeLauncher.launch(arrayOf(perm))
                }
            }
            else -> PermissionChecker.settingsIntent(context, item.key)?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E6)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("🛡️ فحص صلاحيات التطبيق", "🛡️ App permissions check"), style = MaterialTheme.typography.titleMedium, color = Color(0xFF8A4B0B))
            Text(
                tr("التنبيهات الملزمة تحتاج هذه الصلاحيات لتعمل بشكل موثوق:", "Enforced reminders need these permissions to work reliably:"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A4B0B)
            )
            Spacer(Modifier.height(10.dp))
            missing.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("❌ ${item.title}", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(item.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { request(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = HydroWarn)
                    ) { Text(tr("منح", "Grant")) }
                }
            }
        }
    }
}
