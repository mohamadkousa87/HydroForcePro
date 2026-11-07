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
import com.hydroforce.pro.ui.tr
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
            Text(tr("🔄 تحديثات التطبيق", "🔄 App updates"), style = MaterialTheme.typography.titleMedium, color = HydroDeep)
            Text(
                tr(
                    "الإصدار الحالي: ${controller.currentVersionName} (كود ${controller.currentVersionCode})",
                    "Current version: ${controller.currentVersionName} (code ${controller.currentVersionCode})"
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (state) {
                is UpdateUiState.Checking ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(0.dp))
                        Text(tr("  جارٍ التحقق...", "  Checking..."), fontSize = 14.sp)
                    }
                is UpdateUiState.UpToDate ->
                    Text(tr("✅ أنت على أحدث إصدار.", "✅ You're on the latest version."), color = HydroMint, fontWeight = FontWeight.SemiBold)
                is UpdateUiState.Available ->
                    Text(tr("🎉 يتوفّر إصدار جديد: ${state.info.versionName}", "🎉 New version available: ${state.info.versionName}"), color = HydroBlue, fontWeight = FontWeight.Bold)
                is UpdateUiState.Downloading ->
                    Column {
                        Text(tr("⬇️ جارٍ التنزيل... ${(state.progress * 100).toInt()}%", "⬇️ Downloading... ${(state.progress * 100).toInt()}%"), fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        @Suppress("DEPRECATION")
                        LinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth())
                    }
                is UpdateUiState.NeedsPermission ->
                    Text(tr("⚠️ فعّل السماح بتثبيت التطبيقات من هذا المصدر ثم أعد المحاولة.", "⚠️ Allow installing apps from this source, then retry."), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                is UpdateUiState.Error ->
                    Text(tr("تعذّر: ${state.message}", "Failed: ${state.message}"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                else -> {}
            }

            when (state) {
                is UpdateUiState.Available ->
                    Button(onClick = { controller.download(state.info) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(tr("تنزيل وتثبيت ${state.info.versionName} ⬇️", "Download & install ${state.info.versionName} ⬇️"))
                    }
                is UpdateUiState.NeedsPermission ->
                    Button(onClick = { controller.install(state.info, state.file) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(tr("تثبيت الآن", "Install now"))
                    }
                is UpdateUiState.Downloading -> {}
                else ->
                    OutlinedButton(onClick = { controller.check(silent = false) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(tr("التحقق من وجود تحديث", "Check for updates"))
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
        title = { Text(tr("يتوفّر تحديث جديد 🎉", "Update available 🎉")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(tr("الإصدار ${info.versionName}", "Version ${info.versionName}"), fontWeight = FontWeight.Bold, color = HydroBlue)
                Text(tr("ما الجديد:", "What's new:"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(info.notes, fontSize = 14.sp)
                if (downloading != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("جارٍ التنزيل... ${(downloading.progress * 100).toInt()}%", "Downloading... ${(downloading.progress * 100).toInt()}%"), fontSize = 13.sp)
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(progress = downloading.progress, modifier = Modifier.fillMaxWidth())
                }
                if (state is UpdateUiState.NeedsPermission) {
                    Text(tr("⚠️ فعّل السماح بالتثبيت من هذا المصدر ثم اضغط «تثبيت».", "⚠️ Allow installing from this source, then tap \"Install\"."), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.NeedsPermission ->
                    Button(onClick = { controller.install(info, state.file) }) { Text(tr("تثبيت", "Install")) }
                is UpdateUiState.Downloading -> {}
                else ->
                    Button(onClick = { controller.download(info) }) { Text(tr("تحديث الآن", "Update now")) }
            }
        },
        dismissButton = {
            if (downloading == null) TextButton(onClick = { controller.dismiss() }) { Text(tr("لاحقاً", "Later")) }
        }
    )
}
