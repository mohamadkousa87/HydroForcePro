package com.hydroforce.pro.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydroforce.pro.ui.HydroBlue
import com.hydroforce.pro.ui.HydroDeep
import com.hydroforce.pro.ui.HydroMint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Downloaded(val info: UpdateInfo, val file: File) : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var state by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    val currentVersionName: String = UpdateChecker.currentVersionName(context)
    val currentVersionCode: Long = UpdateChecker.currentVersionCode(context)

    /** @param silent when true, stays Idle if already up to date (used for auto-check on launch). */
    fun check(silent: Boolean = false) {
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading) return
        state = UpdateUiState.Checking
        scope.launch {
            try {
                val info = UpdateChecker.checkForUpdate(context)
                state = when {
                    info != null -> UpdateUiState.Available(info)
                    silent -> UpdateUiState.Idle
                    else -> UpdateUiState.UpToDate
                }
            } catch (e: Exception) {
                state = if (silent) UpdateUiState.Idle
                else UpdateUiState.Error(e.message ?: "تعذّر التحقق من التحديثات")
            }
        }
    }

    fun download(info: UpdateInfo) {
        state = UpdateUiState.Downloading(info, 0f)
        scope.launch {
            try {
                val file = UpdateChecker.downloadApk(context, info) { p ->
                    state = UpdateUiState.Downloading(info, p)
                }
                state = UpdateUiState.Downloaded(info, file)
                install(info, file)
            } catch (e: Exception) {
                state = UpdateUiState.Error(e.message ?: "فشل تنزيل التحديث")
            }
        }
    }

    fun install(info: UpdateInfo, file: File) {
        if (!UpdateChecker.canInstall(context)) {
            state = UpdateUiState.NeedsPermission(info, file)
            UpdateChecker.openUnknownSourcesSettings(context)
            return
        }
        UpdateChecker.installApk(context, file)
    }

    fun dismiss() { state = UpdateUiState.Idle }
}

@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current.applicationContext
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember { UpdateController(context, scope) }
}

/** Card for the settings screen: shows current version and a check/install button. */
@Composable
fun UpdateSection(controller: UpdateController) {
    val state = controller.state
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🔄 تحديثات التطبيق", style = MaterialTheme.typography.titleMedium, color = HydroDeep)
            Text(
                "الإصدار الحالي: ${controller.currentVersionName} (كود ${controller.currentVersionCode})",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (state) {
                is UpdateUiState.Checking ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(0.dp))
                        Text("  جارٍ التحقق...", fontSize = 14.sp)
                    }
                is UpdateUiState.UpToDate ->
                    Text("✅ أنت على أحدث إصدار.", color = HydroMint, fontWeight = FontWeight.SemiBold)
                is UpdateUiState.Available ->
                    Text("🎉 يتوفّر إصدار جديد: ${state.info.versionName}", color = HydroBlue, fontWeight = FontWeight.Bold)
                is UpdateUiState.Downloading ->
                    Column {
                        Text("⬇️ جارٍ التنزيل... ${(state.progress * 100).toInt()}%", fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        @Suppress("DEPRECATION")
                        LinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth())
                    }
                is UpdateUiState.NeedsPermission ->
                    Text("⚠️ فعّل السماح بتثبيت التطبيقات من هذا المصدر ثم أعد المحاولة.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                is UpdateUiState.Error ->
                    Text("تعذّر: ${state.message}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                else -> {}
            }

            when (state) {
                is UpdateUiState.Available ->
                    Button(onClick = { controller.download(state.info) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("تنزيل وتثبيت ${state.info.versionName} ⬇️")
                    }
                is UpdateUiState.NeedsPermission ->
                    Button(onClick = { controller.install(state.info, state.file) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("تثبيت الآن")
                    }
                is UpdateUiState.Downloading -> {}
                else ->
                    OutlinedButton(onClick = { controller.check(silent = false) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("التحقق من وجود تحديث")
                    }
            }
        }
    }
}

/** Dialog shown automatically on launch when an update is available. */
@Composable
fun UpdateAvailableDialog(controller: UpdateController) {
    val state = controller.state
    val info = when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.NeedsPermission -> state.info
        else -> null
    } ?: return

    val downloading = state as? UpdateUiState.Downloading

    AlertDialog(
        onDismissRequest = { if (downloading == null) controller.dismiss() },
        title = { Text("يتوفّر تحديث جديد 🎉") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("الإصدار ${info.versionName}", fontWeight = FontWeight.Bold, color = HydroBlue)
                Text("ما الجديد:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(info.notes, fontSize = 14.sp)
                if (downloading != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("جارٍ التنزيل... ${(downloading.progress * 100).toInt()}%", fontSize = 13.sp)
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(progress = downloading.progress, modifier = Modifier.fillMaxWidth())
                }
                if (state is UpdateUiState.NeedsPermission) {
                    Text("⚠️ فعّل السماح بالتثبيت من هذا المصدر ثم اضغط «تثبيت».", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.NeedsPermission ->
                    Button(onClick = { controller.install(info, state.file) }) { Text("تثبيت") }
                is UpdateUiState.Downloading -> {}
                else ->
                    Button(onClick = { controller.download(info) }) { Text("تحديث الآن") }
            }
        },
        dismissButton = {
            if (downloading == null) TextButton(onClick = { controller.dismiss() }) { Text("لاحقاً") }
        }
    )
}
