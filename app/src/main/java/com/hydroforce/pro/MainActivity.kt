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
import com.hydroforce.pro.data.formatMl
import com.hydroforce.pro.ui.DashboardScreen
import com.hydroforce.pro.ui.HydroDeep
import com.hydroforce.pro.ui.HydroTheme
import com.hydroforce.pro.ui.MainViewModel
import com.hydroforce.pro.ui.ProfileScreen
import com.hydroforce.pro.ui.rememberPermissionStatus
import com.hydroforce.pro.update.UpdateAvailableDialog
import com.hydroforce.pro.update.rememberUpdateController

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HydroTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F9FF)) {
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
                    onSettings = { nav.navigate(Routes.SETTINGS) }
                )
            }
        }
        composable(Routes.SETTINGS) {
            ProfileScreen(
                initial = profile,
                isOnboarding = false,
                onSave = { p ->
                    vm.saveProfile(p) {
                        Toast.makeText(context, "تم الحفظ وإعادة جدولة التنبيهات ✅", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { nav.popBackStack() }) { Text("→ إلغاء", color = Color.White) }
                CameraVerificationScreen(
                    title = "شربت ماء الآن؟ وثّق ذلك 📷 (+${formatMl(profile.cupSizeMl)} مل)",
                    onVerified = {
                        vm.onManualDrinkVerified {
                            Toast.makeText(
                                context,
                                "تمت إضافة ${profile.cupSizeMl} مل وإعادة ضبط المؤقت من الآن ⏱️",
                                Toast.LENGTH_LONG
                            ).show()
                            nav.popBackStack()
                        }
                    }
                )
            }
        }
    }
}
