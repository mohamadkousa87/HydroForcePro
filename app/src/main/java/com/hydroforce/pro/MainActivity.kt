package com.hydroforce.pro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hydroforce.pro.camera.CameraVerificationScreen
import com.hydroforce.pro.data.FastingMode
import com.hydroforce.pro.data.ScheduleMath
import com.hydroforce.pro.data.formatMl
import com.hydroforce.pro.ui.DashboardScreen
import com.hydroforce.pro.ui.HydroDeep
import com.hydroforce.pro.ui.HydroTheme
import com.hydroforce.pro.ui.HydrationTipsScreen
import com.hydroforce.pro.ui.MainViewModel
import com.hydroforce.pro.ui.MeetingsScreen
import com.hydroforce.pro.ui.ProfileScreen
import com.hydroforce.pro.ui.StatsScreen
import com.hydroforce.pro.ui.LocalLanguage
import com.hydroforce.pro.ui.tr
import com.hydroforce.pro.data.CsvExporter
import com.hydroforce.pro.ui.rememberPermissionStatus
import com.hydroforce.pro.update.UpdateAvailableDialog
import com.hydroforce.pro.update.rememberUpdateController

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HydroTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HydroNavHost(viewModel)
                }
            }
        }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
    const val MEETINGS = "meetings"
    const val STATS = "stats"
    const val TIPS = "tips"
    const val WORKOUT_CAMERA = "workout_camera"
}

@Composable
private fun HydroNavHost(vm: MainViewModel) {
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    val state = onboarded
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val context = LocalContext.current
    val nav = rememberNavController()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val alarm by vm.alarmState.collectAsStateWithLifecycle()
    val logs by vm.todayLogs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val ringing by vm.ringing.collectAsStateWithLifecycle()
    var permRefresh by remember { mutableIntStateOf(0) }
    val lang = LocalLanguage.current

    // Self-update: check GitHub once on launch and show a dialog if a newer release exists.
    val updateController = rememberUpdateController()
    LaunchedEffect(Unit) { updateController.check(silent = true) }
    UpdateAvailableDialog(updateController)

    NavHost(navController = nav, startDestination = if (state) Routes.DASHBOARD else Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            ProfileScreen(
                initial = profile,
                isOnboarding = true,
                onSave = { p ->
                    vm.saveProfile(p) {
                        nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    }
                },
                onBack = null
            )
        }
        composable(Routes.DASHBOARD) {
            key(permRefresh) {
                val permissions = rememberPermissionStatus(onResume = { vm.ensureScheduledAsync() })
                val drinkType by vm.pendingDrinkType.collectAsStateWithLifecycle()
                val workoutActive by vm.workoutActive.collectAsStateWithLifecycle()
                val bioCount by vm.todayBioCount.collectAsStateWithLifecycle()
                DashboardScreen(
                    profile = profile,
                    result = result,
                    alarm = alarm,
                    logs = logs,
                    now = now,
                    ringing = ringing,
                    permissions = permissions,
                    onPermissionsChanged = { permRefresh++ },
                    onDrinkNow = { nav.navigate(Routes.CAMERA) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onMeetings = { nav.navigate(Routes.MEETINGS) },
                    onStats = { nav.navigate(Routes.STATS) },
                    onTips = { nav.navigate(Routes.TIPS) },
                    selectedDrinkType = drinkType,
                    onDrinkTypeSelected = { vm.setDrinkType(it) },
                    workoutActive = workoutActive,
                    onStartWorkout = { duration ->
                        val nowT = System.currentTimeMillis()
                        val dryFast = profile.fastingMode == FastingMode.DRY_FAST &&
                            ScheduleMath.isFastingDay(nowT, profile) && ScheduleMath.inFastWindow(nowT, profile)
                        if (dryFast) {
                            vm.startWorkout(duration) {
                                Toast.makeText(context, tr(lang, "بدأ التمرين — أُضيف +500 مل بعد انتهاء الصيام", "Workout started — +500 ml added after the fast ends"), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            vm.setPendingWorkout(duration)
                            nav.navigate(Routes.WORKOUT_CAMERA)
                        }
                    },
                    onEndWorkout = { vm.endWorkout() },
                    todayBioCount = bioCount,
                    onBioGrade = { grade ->
                        vm.applyBioCheck(grade) { o ->
                            when {
                                o.openCamera -> {
                                    Toast.makeText(context, tr(lang, "💧 جفاف شديد — اشرب كوب ماء الآن", "💧 Severe dehydration — drink a cup now"), Toast.LENGTH_LONG).show()
                                    nav.navigate(Routes.CAMERA)
                                }
                                o.flushWarning -> Toast.makeText(context, tr(lang, "طرد سريع للماء! جرّب 200–250 مل على دفعات", "Rapid flush! Try 200–250 ml in smaller sips"), Toast.LENGTH_LONG).show()
                                o.addedMl > 0 -> Toast.makeText(context, tr(lang, "أضفنا ${o.addedMl} مل لهدف اليوم 💧", "Added ${o.addedMl} ml to today's goal 💧"), Toast.LENGTH_SHORT).show()
                                else -> {}
                            }
                        }
                    },
                    onUndoBio = { vm.undoBioCheck() }
                )
            }
        }
        composable(Routes.WORKOUT_CAMERA) {
            val duration by vm.pendingWorkoutDuration.collectAsStateWithLifecycle()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(HydroDeep, Color(0xFF0A2540))))
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                TextButton(onClick = { nav.popBackStack() }) { Text(tr("→ إلغاء", "→ Cancel"), color = Color.White) }
                CameraVerificationScreen(
                    title = tr("وثّق وجود مطارة الماء معك 🏋️", "Verify your water bottle is with you 🏋️"),
                    defaultAmountMl = profile.cupSizeMl,
                    onVerified = { _, _ ->
                        vm.startWorkout(duration) {
                            Toast.makeText(context, tr(lang, "بدأ التمرين 🏋️ — رشفات كل 15 دقيقة", "Workout started 🏋️ — sips every 15 min"), Toast.LENGTH_LONG).show()
                            nav.popBackStack()
                        }
                    }
                )
            }
        }
        composable(Routes.TIPS) {
            HydrationTipsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.STATS) {
            val stats by vm.stats.collectAsStateWithLifecycle()
            StatsScreen(
                stats = stats,
                onExport = {
                    vm.exportCsv { file ->
                        if (file != null) {
                            CsvExporter.share(context, file)
                        } else {
                            Toast.makeText(context, "تعذّر تصدير السجل", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.MEETINGS) {
            val meetings by vm.meetings.collectAsStateWithLifecycle()
            MeetingsScreen(
                meetings = meetings,
                onAdd = { start, end, title -> vm.addMeeting(start, end, title) },
                onRemove = { vm.removeMeeting(it) },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            ProfileScreen(
                initial = profile,
                isOnboarding = false,
                onSave = { p ->
                    vm.saveProfile(p) {
                        Toast.makeText(context, tr(lang, "تم حفظ الإعدادات ✅", "Settings saved ✅"), Toast.LENGTH_SHORT).show()
                        nav.popBackStack()
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.CAMERA) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(HydroDeep, Color(0xFF0A2540))))
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                TextButton(onClick = { nav.popBackStack() }) { Text(tr("→ إلغاء", "→ Cancel"), color = Color.White) }
                CameraVerificationScreen(
                    title = tr(
                        "شربت ماء الآن؟ وثّق ذلك 📷",
                        "Drinking now? Verify it 📷"
                    ),
                    defaultAmountMl = profile.cupSizeMl,
                    onVerified = { amountMl, viaPromise ->
                        vm.onManualDrinkVerified(amountMl, viaPromise) {
                            val msg = if (viaPromise) {
                                tr(lang, "تم قبول التعهد، ثقتنا بك عالية! لا تنسَ شرب الماء 💙", "Promise accepted — we trust you! Don't forget to drink 💙")
                            } else {
                                tr(lang, "تمت إضافة $amountMl مل وتحديث المؤقت ⏱️", "Added $amountMl ml and updated the timer ⏱️")
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            nav.popBackStack()
                        }
                    }
                )
            }
        }
    }
}
