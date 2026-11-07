package com.hydroforce.pro.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.camera.CameraVerificationScreen
import com.hydroforce.pro.data.AlarmState
import com.hydroforce.pro.data.DrinkType
import com.hydroforce.pro.data.MAX_SNOOZES
import com.hydroforce.pro.data.SNOOZE_OPTIONS
import com.hydroforce.pro.data.SettingsRepository
import com.hydroforce.pro.ui.DrinkTypePicker
import com.hydroforce.pro.ui.HydroDeep
import com.hydroforce.pro.ui.HydroTheme
import com.hydroforce.pro.ui.HydroWarn
import com.hydroforce.pro.ui.LocalLanguage
import com.hydroforce.pro.ui.tr
import kotlinx.coroutines.launch

/**
 * Full-screen alarm shown over the lock screen. Back is disabled: the only exits are
 * a camera-verified drink or the limited emergency snooze.
 */
class AlarmVerifyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@AlarmVerifyActivity,
                    "لا يمكن الرجوع — وثّق شرب الماء بالكاميرا أو استخدم الغفوة الطارئة",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        val initialDiscreet = intent.getBooleanExtra(AlarmService.EXTRA_DISCREET, false)

        setContent {
            HydroTheme {
                AlarmScreen(
                    initialDiscreet = initialDiscreet,
                    onDone = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(initialDiscreet: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = androidx.compose.runtime.remember { SettingsRepository(context) }
    val alarmState by repo.alarmState.collectAsState(initial = AlarmState())
    val ringing by AlarmService.ringing.collectAsState()
    val muted by AlarmService.muted.collectAsState()
    val discreetLive by AlarmService.discreet.collectAsState()
    val discreet = if (ringing) discreetLive else initialDiscreet
    val snoozesLeft = (MAX_SNOOZES - alarmState.snoozeUsed).coerceAtLeast(0)
    var drinkType by remember { mutableStateOf(DrinkType.WATER) }
    val lang = LocalLanguage.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HydroDeep, Color(0xFF0A2540))))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💧", fontSize = 56.sp)
        Text(
            tr("حان وقت شرب الماء!", "Time to drink water!"),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (discreet) tr("وضع العمل الهادئ 💼", "Quiet work mode 💼") else tr("وضع المُلزِم الكامل 🔔", "Full enforcer mode 🔔"),
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (discreet) Color(0xFF1F7A5C) else HydroWarn)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))

        if (discreet && ringing) {
            Button(
                onClick = { AlarmService.mute(context) },
                enabled = !muted,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (muted) tr("🔇 تم كتم الصوت والاهتزاز", "🔇 Muted sound & vibration") else tr("🔇 كتم الصوت بلمسة (اجتماع)", "🔇 Mute (meeting)"), color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(tr("🥤 ماذا تشرب الآن؟", "🥤 What are you drinking?"), color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        DrinkTypePicker(selected = drinkType, onSelected = { drinkType = it })
        Spacer(Modifier.height(12.dp))

        CameraVerificationScreen(
            title = tr("أثبت أنك تشرب الآن", "Prove you're drinking now"),
            onVerified = {
                scope.launch {
                    HydrationManager.recordVerifiedDrink(context, HydrationManager.SOURCE_ALARM, drinkType)
                    AlarmService.stop(context)
                    onDone()
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        if (snoozesLeft > 0) {
            Text(
                tr("⏰ تأجيل التنبيه — متبقٍ $snoozesLeft من $MAX_SNOOZES", "⏰ Snooze — $snoozesLeft of $MAX_SNOOZES left"),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SNOOZE_OPTIONS.forEach { minutes ->
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                if (HydrationManager.snooze(context, minutes)) {
                                    AlarmService.stop(context)
                                    Toast.makeText(context, tr(lang, "تم تأجيل التنبيه $minutes دقائق", "Reminder postponed $minutes min"), Toast.LENGTH_SHORT).show()
                                    onDone()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(tr("⏰ $minutes دقائق", "⏰ $minutes min"), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Text(
                tr("⛔ استُنفدت مرات التأجيل — التوثيق بالكاميرا إلزامي", "⛔ No snoozes left — camera verification is required"),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
