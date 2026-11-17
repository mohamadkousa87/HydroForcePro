package com.hydroforce.pro.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.hydroforce.pro.data.DrinkType
import com.hydroforce.pro.data.Language
import com.hydroforce.pro.data.SavedCupSignature
import com.hydroforce.pro.data.VolumeUnit
import com.hydroforce.pro.data.formatVolume
import com.hydroforce.pro.data.volumeUnitLabel
import com.hydroforce.pro.ui.CloudAiDialog
import com.hydroforce.pro.ui.HydroDanger
import com.hydroforce.pro.ui.LocalLanguage
import com.hydroforce.pro.ui.tr
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private val VeroCyan = Color(0xFF00E5FF)
private val VeroGreen = Color(0xFF00E676)
private val VeroRed = Color(0xFFFF5252)
private val VeroOrange = Color(0xFFFF8F3A)
private val RoyalBlue = Color(0xFF003B99)

private val VOLUME_OPTIONS = listOf(100, 150, 200, 250, 300, 350)
private val REGISTER_DRINKS = listOf(
    DrinkType.WATER, DrinkType.HERBAL, DrinkType.MILK, DrinkType.LABAN,
    DrinkType.FRESH_JUICE, DrinkType.COFFEE_ESPRESSO, DrinkType.ENERGY_DRINK
)

private fun cropCenter(src: Bitmap, fraction: Float = 0.6f): Bitmap {
    val w = (src.width * fraction).toInt().coerceIn(1, src.width)
    val h = (src.height * fraction).toInt().coerceIn(1, src.height)
    val x = (src.width - w) / 2
    val y = (src.height - h) / 2
    return Bitmap.createBitmap(src, x, y, w, h)
}

private sealed interface VerifyState {
    data object Ready : VerifyState
    data object Analyzing : VerifyState
    data class Rejected(val seen: List<String>, val isObject: Boolean) : VerifyState
    data class Verified(val matched: String) : VerifyState
    data class Error(val message: String) : VerifyState
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeLiveFrame(
    proxy: ImageProxy,
    labeler: ImageLabeler,
    lastTs: AtomicLong,
    lastHeavyTs: AtomicLong,
    savedCups: List<SavedCupSignature>,
    tflite: OptionalTfliteModel?,
    ocr: VeroSipOcr?,
    barcode: VeroSipBarcode?,
    cloudMode: Boolean,
    executor: Executor,
    onResult: (VeroSipVisionEngine.Decision, List<Pair<String, Float>>) -> Unit
) {
    val now = System.currentTimeMillis()
    if (now - lastTs.get() < 500) {
        proxy.close()
        return
    }
    lastTs.set(now)
    val doHeavy = now - lastHeavyTs.get() > 1200
    if (doHeavy) lastHeavyTs.set(now)
    val full = try { proxy.toBitmap() } catch (e: Exception) { null }
    if (full == null) {
        proxy.close()
        return
    }
    val rot = proxy.imageInfo.rotationDegrees
    val cropped = runCatching { cropCenter(full) }.getOrDefault(full)
    labeler.process(InputImage.fromBitmap(cropped, rot))
        .addOnSuccessListener(executor) { labels ->
            val pairs = labels.map { it.text to it.confidence }
            val decision = VeroSipVisionEngine.decide(cropped, pairs, savedCups, tflite, ocr, barcode, doHeavy, cloudMode)
            onResult(decision, VeroSipVisionEngine.relevant(pairs))
        }
        .addOnCompleteListener(executor) {
            proxy.close()
            if (cropped !== full) runCatching { cropped.recycle() }
            runCatching { full.recycle() }
        }
}

@Composable
fun CameraVerificationScreen(
    modifier: Modifier = Modifier,
    title: String = "وثّق شرب الماء بالكاميرا",
    defaultAmountMl: Int = 400,
    savedCups: List<SavedCupSignature> = emptyList(),
    volumeUnit: VolumeUnit = VolumeUnit.ML,
    cloudAiEnabled: Boolean = false,
    geminiApiKey: String = "",
    onCloudAiEnabledChange: (Boolean) -> Unit = {},
    onGeminiKeyChange: (String) -> Unit = {},
    onDrinkTypeSelected: (String) -> Unit = {},
    onRegisterCup: (name: String, volumeMl: Int, drinkTypeName: String, barcodeOrKeyword: String) -> Unit = { _, _, _, _ -> },
    onDeleteCup: (Long) -> Unit = {},
    onVerified: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("ضع الكأس أو العبوة داخل الإطار ثم اضغط توثيق الشرب", "Put the cup/bottle inside the frame, then tap Verify"),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        if (hasPermission) {
            CameraContent(
                defaultAmountMl, savedCups, volumeUnit, cloudAiEnabled, geminiApiKey,
                onCloudAiEnabledChange, onGeminiKeyChange, onDrinkTypeSelected, onRegisterCup, onDeleteCup, onVerified
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📷", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("يجب منح إذن الكاميرا لتوثيق شرب الماء", "Camera permission is required to verify drinking"),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(tr("منح إذن الكاميرا", "Grant camera permission"))
                }
            }
        }
    }
}

@Composable
private fun CameraContent(
    defaultAmountMl: Int,
    savedCups: List<SavedCupSignature>,
    volumeUnit: VolumeUnit,
    cloudAiEnabled: Boolean,
    geminiApiKey: String,
    onCloudAiEnabledChange: (Boolean) -> Unit,
    onGeminiKeyChange: (String) -> Unit,
    onDrinkTypeSelected: (String) -> Unit,
    onRegisterCup: (String, Int, String, String) -> Unit,
    onDeleteCup: (Long) -> Unit,
    onVerified: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lang = LocalLanguage.current
    var state by remember { mutableStateOf<VerifyState>(VerifyState.Ready) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var liveLabels by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var isTargetLocked by remember { mutableStateOf(false) }
    var isRejectedObject by remember { mutableStateOf(false) }
    var lockedSavedName by remember { mutableStateOf<String?>(null) }
    var lockedOcrLabel by remember { mutableStateOf<String?>(null) }
    var lastValidLabel by remember { mutableStateOf<String?>(null) }
    var lastValidTs by remember { mutableLongStateOf(0L) }
    var overrideVerify by remember { mutableStateOf(false) }
    var showRegister by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf("") }
    var pinnedCup by remember { mutableStateOf<SavedCupSignature?>(null) }
    var customVolText by remember { mutableStateOf("") }
    var selectedAmount by remember {
        mutableIntStateOf(VOLUME_OPTIONS.minByOrNull { abs(it - defaultAmountMl) } ?: 200)
    }

    // Fresh snapshots for the (set-once) analyzer.
    val savedCupsRef = remember { mutableStateOf(savedCups) }
    savedCupsRef.value = savedCups
    val cloudEnabledRef = remember { mutableStateOf(cloudAiEnabled) }
    cloudEnabledRef.value = cloudAiEnabled
    val cloudKeyRef = remember { mutableStateOf(geminiApiKey) }
    cloudKeyRef.value = geminiApiKey

    val tflite = remember { OptionalTfliteModel(context) }
    val ocr = remember { VeroSipOcr() }
    val barcode = remember { VeroSipBarcode() }
    val lastHeavyTs = remember { AtomicLong(0L) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val labeler = remember {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.25f).build())
    }
    val liveLabeler = remember {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.25f).build())
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastAnalysisTs = remember { AtomicLong(0L) }
    val imageAnalysis = remember {
        ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    }

    val verified = state as? VerifyState.Verified

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                    analyzeLiveFrame(
                        proxy, liveLabeler, lastAnalysisTs, lastHeavyTs,
                        savedCupsRef.value, tflite, ocr, barcode,
                        cloudEnabledRef.value && cloudKeyRef.value.isNotBlank(), analysisExecutor
                    ) { decision, rel ->
                        liveLabels = rel
                        isTargetLocked = decision.locked
                        isRejectedObject = decision.rejected
                        if (decision.locked) {
                            lockedSavedName = decision.savedName
                            lockedOcrLabel = decision.ocrLabel
                            lastValidLabel = decision.label
                            lastValidTs = System.currentTimeMillis()
                        } else {
                            lockedSavedName = null
                            lockedOcrLabel = null
                        }
                    }
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis
                )
            } catch (e: Exception) {
                state = VerifyState.Error(tr(lang, "تعذر تشغيل الكاميرا: ${e.message ?: ""}", "Couldn't start the camera: ${e.message ?: ""}"))
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
            runCatching { analysisExecutor.shutdown() }
        }
    }
    DisposableEffect(Unit) {
        onDispose { labeler.close(); liveLabeler.close() }
    }

    LaunchedEffect(verified) {
        if (verified != null) {
            playSuccessTone()
            delay(1100)
            onVerified(selectedAmount, overrideVerify)
        }
    }

    // Manual verify: a tapped saved cup or a live lock accepts instantly; otherwise capture & scan.
    fun capture() {
        state = VerifyState.Analyzing
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val full = try { image.toBitmap() } catch (e: Exception) { null } finally { image.close() }
                    if (full == null) {
                        failedAttempts++
                        state = VerifyState.Error(tr(lang, "تعذرت قراءة الصورة، حاول مرة أخرى", "Couldn't read the image, try again"))
                        return
                    }
                    val cropped = runCatching { cropCenter(full) }.getOrDefault(full)
                    analysisExecutor.execute {
                        try {
                            // Cloud mode: the cloud is the sole judge. On any failure we INFORM & STOP
                            // (no silent local fallback), per the chosen design.
                            if (cloudEnabledRef.value && cloudKeyRef.value.isNotBlank()) {
                                when (CloudVisionVerifier.verify(cropped, cloudKeyRef.value)) {
                                    is CloudVisionVerifier.Result.Positive -> { overrideVerify = false; state = VerifyState.Verified("cloud") }
                                    CloudVisionVerifier.Result.Negative -> { failedAttempts++; state = VerifyState.Rejected(emptyList(), isObject = true) }
                                    CloudVisionVerifier.Result.Unavailable -> state = VerifyState.Error(
                                        tr(lang, "تعذّر الفحص السحابي — تحقق من الإنترنت أو المفتاح وحاول ثانية 🌐", "Cloud check failed — check your internet or key and try again 🌐")
                                    )
                                }
                                return@execute
                            }
                            val labels = runCatching {
                                Tasks.await(labeler.process(InputImage.fromBitmap(cropped, rotation)), 1500, TimeUnit.MILLISECONDS)
                            }.getOrNull()
                            if (labels == null) {
                                failedAttempts++
                                state = VerifyState.Error(tr(lang, "فشل التحليل، حاول مرة أخرى", "Analysis failed, try again"))
                            } else {
                                val pairs = labels.map { it.text to it.confidence }
                                val d = VeroSipVisionEngine.decide(cropped, pairs, savedCupsRef.value, tflite, ocr, barcode, useHeavy = true)
                                when {
                                    d.locked -> { overrideVerify = false; state = VerifyState.Verified(d.label) }
                                    d.rejected -> { failedAttempts++; state = VerifyState.Rejected(emptyList(), isObject = true) }
                                    else -> { failedAttempts++; state = VerifyState.Rejected(VeroSipVisionEngine.relevant(pairs).map { it.first }, isObject = false) }
                                }
                            }
                        } finally {
                            if (cropped !== full) runCatching { cropped.recycle() }
                            runCatching { full.recycle() }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    failedAttempts++
                    state = VerifyState.Error(tr(lang, "فشل الالتقاط: ${exception.message ?: ""}", "Capture failed: ${exception.message ?: ""}"))
                }
            }
        )
    }

    fun onVerifyClick() {
        val now = System.currentTimeMillis()
        val cloudMode = cloudAiEnabled && geminiApiKey.isNotBlank()
        when {
            // A real check must have passed: live lock (local hint or saved cup) within 2s.
            isTargetLocked || (lastValidLabel != null && now - lastValidTs < 2000) -> {
                overrideVerify = false
                state = VerifyState.Verified(lastValidLabel ?: "cup")
            }
            // Cloud mode: the cloud is the judge — check this frame now.
            cloudMode -> capture()
            // Local mode, nothing locked → do NOT record; tell the user to aim first.
            else -> state = VerifyState.Error(
                tr(lang, "يرجى توجيه الكاميرا نحو الكأس أو العبوة أولاً ⚠️", "Point the camera at the cup or bottle first ⚠️")
            )
        }
    }

    // Capture a frame and read its barcode / label keyword for registration.
    fun captureForRegister() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val full = try { image.toBitmap() } catch (e: Exception) { null } finally { image.close() }
                    if (full == null) return
                    val cropped = runCatching { cropCenter(full) }.getOrDefault(full)
                    analysisExecutor.execute {
                        val code = barcode.read(cropped, rotation)
                        val key = code ?: ocr.read(cropped, rotation).let { out ->
                            out.accept ?: ocr.bestKeyword(out.rawText)
                        }
                        pendingKey = key ?: ""
                        if (cropped !== full) runCatching { cropped.recycle() }
                        runCatching { full.recycle() }
                        showRegister = true
                    }
                }
                override fun onError(exception: ImageCaptureException) {}
            }
        )
    }

    // ---- Dialogs ----
    if (showCloud) {
        CloudAiDialog(
            enabled = cloudAiEnabled,
            apiKey = geminiApiKey,
            onEnabledChange = onCloudAiEnabledChange,
            onKeyChange = onGeminiKeyChange,
            onDismiss = { showCloud = false }
        )
    }

    if (showRegister) {
        RegisterCupDialog(
            selectedAmount = selectedAmount,
            volumeUnit = volumeUnit,
            lang = lang,
            pendingKey = pendingKey,
            onVolumeChange = { selectedAmount = it },
            onCancel = { showRegister = false; pendingKey = "" },
            onSave = { name, vol, drinkName ->
                onRegisterCup(name, vol, drinkName, pendingKey)
                showRegister = false
                pendingKey = ""
            }
        )
    }

    if (showManage) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            confirmButton = { TextButton(onClick = { showManage = false }) { Text(tr("تم", "Done")) } },
            title = { Text(tr("أكوابي المحفوظة ⭐", "My saved cups ⭐")) },
            text = {
                Column {
                    if (savedCups.isEmpty()) {
                        Text(tr("لا أكواب محفوظة بعد.", "No saved cups yet."))
                    } else {
                        savedCups.forEach { cup ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐ ${cup.name} (${cup.defaultVolumeMl})", modifier = Modifier.weight(1f))
                                TextButton(onClick = { onDeleteCup(cup.id) }) { Text("🗑️") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        )
    }

    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(
                3.dp,
                when {
                    verified != null || isTargetLocked -> VeroGreen
                    isRejectedObject || state is VerifyState.Rejected || state is VerifyState.Error -> VeroRed
                    else -> Color.White.copy(alpha = 0.5f)
                },
                RoundedCornerShape(28.dp)
            )
            .background(Color.Black)
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val locked = verified != null || isTargetLocked
            val rejected = !locked && (isRejectedObject || state is VerifyState.Rejected || state is VerifyState.Error)
            val bracketColor = when {
                locked -> VeroGreen
                rejected -> VeroRed
                else -> VeroCyan.copy(alpha = 0.7f)
            }
            val side = minOf(size.width, size.height) * 0.6f
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val right = left + side
            val bottom = top + side
            val arm = side * 0.18f
            val sw = 4.dp.toPx()
            fun l(a: Offset, b: Offset, c: Color, s: Float) = drawLine(c, a, b, strokeWidth = s, cap = StrokeCap.Round)
            if (locked) {
                val gw = sw * 3f
                val gc = VeroGreen.copy(alpha = pulse * 0.5f)
                l(Offset(left, top), Offset(left + arm, top), gc, gw); l(Offset(left, top), Offset(left, top + arm), gc, gw)
                l(Offset(right, top), Offset(right - arm, top), gc, gw); l(Offset(right, top), Offset(right, top + arm), gc, gw)
                l(Offset(left, bottom), Offset(left + arm, bottom), gc, gw); l(Offset(left, bottom), Offset(left, bottom - arm), gc, gw)
                l(Offset(right, bottom), Offset(right - arm, bottom), gc, gw); l(Offset(right, bottom), Offset(right, bottom - arm), gc, gw)
            }
            l(Offset(left, top), Offset(left + arm, top), bracketColor, sw); l(Offset(left, top), Offset(left, top + arm), bracketColor, sw)
            l(Offset(right, top), Offset(right - arm, top), bracketColor, sw); l(Offset(right, top), Offset(right, top + arm), bracketColor, sw)
            l(Offset(left, bottom), Offset(left + arm, bottom), bracketColor, sw); l(Offset(left, bottom), Offset(left, bottom - arm), bracketColor, sw)
            l(Offset(right, bottom), Offset(right - arm, bottom), bracketColor, sw); l(Offset(right, bottom), Offset(right, bottom - arm), bracketColor, sw)

            val cx = size.width / 2f
            val cy = size.height / 2f
            val dw = side * 0.26f
            val dh = side * 0.34f
            val drop = Path().apply {
                moveTo(cx, cy - dh / 2f)
                cubicTo(cx + dw * 0.62f, cy - dh * 0.1f, cx + dw * 0.5f, cy + dh * 0.5f, cx, cy + dh * 0.5f)
                cubicTo(cx - dw * 0.5f, cy + dh * 0.5f, cx - dw * 0.62f, cy - dh * 0.1f, cx, cy - dh / 2f)
                close()
            }
            if (locked) {
                drawPath(drop, RoyalBlue.copy(alpha = 0.8f))
                drawPath(drop, VeroCyan, style = Stroke(2.5.dp.toPx()))
                val chk = Path().apply {
                    moveTo(cx - dw * 0.22f, cy + dh * 0.05f)
                    lineTo(cx - dw * 0.02f, cy + dh * 0.22f)
                    lineTo(cx + dw * 0.3f, cy - dh * 0.12f)
                }
                drawPath(chk, VeroGreen, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            } else {
                drawPath(drop, Color.White.copy(alpha = 0.22f))
                drawPath(drop, VeroCyan.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx()))
            }
        }

        // Torch + Cloud AI (top-end).
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (cloudAiEnabled) VeroGreen.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f))
                    .clickable { showCloud = true },
                contentAlignment = Alignment.Center
            ) { Text("☁️", fontSize = 18.sp) }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable { torchOn = !torchOn; camera?.cameraControl?.enableTorch(torchOn) },
                contentAlignment = Alignment.Center
            ) { Text(if (torchOn) "🔦" else "💡", fontSize = 20.sp) }
        }

        // Register / manage (top-start).
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                    .clickable { captureForRegister() }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text(tr("+ تعريف كأسي ⭐", "+ Register cup ⭐"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            if (savedCups.isNotEmpty()) {
                Box(
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showManage = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("⭐${savedCups.size}", color = Color.White, fontSize = 12.sp) }
            }
        }

        if (verified == null) {
            val cloudMode = cloudAiEnabled && geminiApiKey.isNotBlank()
            val highlight = lockedSavedName != null || (isTargetLocked && lockedOcrLabel != null)
            val bar = when {
                lockedSavedName != null -> tr("تم التعرف على عبوتك: ${lockedSavedName} ⭐", "Recognized your cup: ${lockedSavedName} ⭐")
                cloudMode -> tr("الوضع السحابي 🌐 — سيُدقّق عند الضغط", "Cloud mode 🌐 — verified on tap")
                isTargetLocked && lockedOcrLabel != null -> tr("تم قراءة ملصق العبوة: ${lockedOcrLabel} ✓", "Label read: ${lockedOcrLabel} ✓")
                liveLabels.isNotEmpty() -> tr("الرؤية: ", "Vision: ") + liveLabels.joinToString("  |  ") { "${it.first} ${(it.second * 100).toInt()}%" }
                else -> null
            }
            if (bar != null) {
                Text(
                    bar, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (highlight) VeroGreen.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (state is VerifyState.Analyzing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (cloudAiEnabled && geminiApiKey.isNotBlank()) tr("جارٍ التحقق بالذكاء السحابي...", "Verifying with Cloud AI...")
                        else tr("جارٍ التحقق بالذكاء الاصطناعي...", "Verifying with AI..."),
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = verified != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(VeroGreen), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(tr("تم التحقق! أحسنت 💧", "Verified! Well done 💧"), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    when (val s = state) {
        is VerifyState.Rejected -> MessageBox(
            if (s.isObject) {
                tr("رُصد جسم غير مسموح (تدخين/إلكترونيات)! صوّر الكأس أو العبوة فقط 🚫", "A non-allowed object (smoking/electronics) was detected! Capture only the cup or bottle 🚫")
            } else {
                tr("لم يتم رصد كأس بوضوح — قرّب العبوة داخل الإطار", "No clear cup detected — move the bottle inside the frame")
            }
        )
        is VerifyState.Error -> MessageBox(s.message)
        else -> {}
    }

    // Saved cups shortcut bar.
    if (savedCups.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(tr("أكوابي ⭐", "My cups ⭐"), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            savedCups.forEach { cup ->
                FilterChip(
                    selected = pinnedCup?.id == cup.id,
                    onClick = {
                        // Tapping a saved cup ONLY selects it as the current target (volume + drink
                        // type). It never records a drink — that still needs a real camera check.
                        pinnedCup = if (pinnedCup?.id == cup.id) null else cup
                        if (pinnedCup != null) {
                            selectedAmount = cup.defaultVolumeMl
                            customVolText = ""
                            onDrinkTypeSelected(cup.drinkTypeName)
                        }
                    },
                    enabled = verified == null,
                    label = { Text("⭐ ${cup.name} ${formatVolume(cup.defaultVolumeMl, volumeUnit, lang)}") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VeroGreen, selectedLabelColor = Color.Black, labelColor = Color.White)
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(tr("كم ستشرب؟", "How much?"), color = Color.White, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VOLUME_OPTIONS.forEach { ml ->
            FilterChip(
                selected = customVolText.isBlank() && selectedAmount == ml,
                onClick = { selectedAmount = ml; customVolText = "" },
                enabled = verified == null,
                label = { Text(formatVolume(ml, volumeUnit, lang)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VeroCyan, selectedLabelColor = Color.Black, labelColor = Color.White)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = customVolText,
        onValueChange = { v ->
            customVolText = v.filter { it.isDigit() }.take(4)
            customVolText.toIntOrNull()?.let { if (it in 1..5000) selectedAmount = it }
        },
        singleLine = true,
        enabled = verified == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        label = { Text(tr("حجم مخصص", "Custom amount"), color = Color.White.copy(alpha = 0.85f)) },
        suffix = { Text(volumeUnitLabel(volumeUnit, lang), color = Color.White, fontWeight = FontWeight.Bold) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VeroCyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
            cursorColor = VeroCyan
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = { onVerifyClick() },
        enabled = state !is VerifyState.Analyzing && verified == null,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (isTargetLocked) VeroGreen else Color.White, contentColor = Color(0xFF0A1118))
    ) {
        Text(
            if (isTargetLocked) tr("توثيق الشرب ✓", "Verify drink ✓")
            else tr("📷 توثيق الشرب (${formatVolume(selectedAmount, volumeUnit, lang)})", "📷 Verify drink (${formatVolume(selectedAmount, volumeUnit, lang)})"),
            fontSize = 17.sp, fontWeight = FontWeight.Bold
        )
    }

    if (failedAttempts >= 3 && verified == null) {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { overrideVerify = true; state = VerifyState.Verified("promise") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VeroOrange, contentColor = Color.White)
        ) { Text(tr("تأكيد بالتعهد 🤝 (تجاوز)", "Confirm by promise 🤝 (bypass)"), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterCupDialog(
    selectedAmount: Int,
    volumeUnit: VolumeUnit,
    lang: Language,
    pendingKey: String,
    onVolumeChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onSave: (name: String, volumeMl: Int, drinkTypeName: String) -> Unit
) {
    val containers = listOf(
        tr("كأس شفاف", "Clear glass"), tr("كأس زجاج", "Glass"), tr("عبوة بلاستيك", "Plastic bottle"),
        tr("زجاجة", "Bottle"), tr("علبة / كرتون", "Carton"), tr("مج / Mug", "Mug"), tr("مطارة رياضية", "Sports flask")
    )
    var container by remember { mutableStateOf(containers.first()) }
    var drink by remember { mutableStateOf(DrinkType.WATER) }
    var customName by remember { mutableStateOf("") }
    var volText by remember { mutableStateOf(selectedAmount.toString()) }
    val vol = volText.toIntOrNull()?.coerceIn(50, 2500) ?: selectedAmount
    val drinkLabel = tr(lang, drink.label, drink.labelEn)
    val finalName = customName.trim().ifBlank { "$container - $drinkLabel" }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { onVolumeChange(vol); onSave(finalName, vol, drink.name) }) {
                Text(tr("حفظ الكوب ⭐", "Save cup ⭐"))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("إلغاء", "Cancel")) } },
        title = { Text(tr("تعريف كأسي/عبوتي ⭐", "Register my cup/bottle ⭐")) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(tr("نوع الوعاء:", "Container type:"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    containers.forEach { c ->
                        FilterChip(selected = container == c, onClick = { container = c }, label = { Text(c) })
                    }
                }
                Text(tr("نوع المشروب:", "Drink type:"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REGISTER_DRINKS.forEach { dt ->
                        FilterChip(selected = drink == dt, onClick = { drink = dt }, label = { Text("${dt.emoji} ${tr(lang, dt.label, dt.labelEn)}") })
                    }
                }
                OutlinedTextField(
                    value = volText,
                    onValueChange = { v -> volText = v.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(tr("الحجم (مل)", "Volume (ml)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    singleLine = true,
                    label = { Text(tr("اسم مخصص للوعاء أو المشروب (اختياري)...", "Custom name (optional)...")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (pendingKey.isNotBlank()) tr("تم رصد معرّف للعبوة ✓ (تعرّف تلقائي لاحقاً)", "Label/barcode captured ✓ (auto-recognized)")
                    else tr("لم يُرصد باركود/نص — يُحفظ بالاسم وتختاره من الشريط.", "No barcode/text — saved by name; pick it from the bar."),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pendingKey.isNotBlank()) VeroGreen else Color.Gray
                )
            }
        }
    )
}

@Composable
private fun MessageBox(text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HydroDanger.copy(alpha = 0.9f)).padding(14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚠️ $text", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private fun playSuccessTone() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 400)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
    }
}
