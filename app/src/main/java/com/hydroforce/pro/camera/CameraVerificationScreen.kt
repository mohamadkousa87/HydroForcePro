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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.hydroforce.pro.data.SavedCupSignature
import com.hydroforce.pro.ui.HydroDanger
import com.hydroforce.pro.ui.LocalLanguage
import com.hydroforce.pro.ui.tr
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private val VeroCyan = Color(0xFF00E5FF)
private val VeroGreen = Color(0xFF00E676)
private val VeroRed = Color(0xFFFF5252)
private val VeroOrange = Color(0xFFFF8F3A)
private val RoyalBlue = Color(0xFF003B99)

private val VOLUME_OPTIONS = listOf(100, 200, 300, 400, 500)

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
    savedCups: List<SavedCupSignature>,
    tflite: OptionalTfliteModel?,
    onResult: (VeroSipVisionEngine.Decision, List<Pair<String, Float>>) -> Unit
) {
    val now = System.currentTimeMillis()
    if (now - lastTs.get() < 500) {
        proxy.close()
        return
    }
    lastTs.set(now)
    val full = try { proxy.toBitmap() } catch (e: Exception) { null }
    if (full == null) {
        proxy.close()
        return
    }
    val rot = proxy.imageInfo.rotationDegrees
    val cropped = runCatching { cropCenter(full) }.getOrDefault(full)
    labeler.process(InputImage.fromBitmap(cropped, rot))
        .addOnSuccessListener { labels ->
            val pairs = labels.map { it.text to it.confidence }
            val decision = VeroSipVisionEngine.decide(cropped, pairs, savedCups, tflite)
            onResult(decision, VeroSipVisionEngine.relevant(pairs))
        }
        .addOnCompleteListener {
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
    onRegisterCup: (name: String, volumeMl: Int, signature: FloatArray) -> Unit = { _, _, _ -> },
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
            tr("ضع الكأس داخل الإطار الأوسط ثم اضغط توثيق الشرب", "Put the cup inside the center frame, then tap Verify"),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        if (hasPermission) {
            CameraContent(defaultAmountMl, savedCups, onRegisterCup, onDeleteCup, onVerified)
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
    onRegisterCup: (String, Int, FloatArray) -> Unit,
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
    var lastValidLabel by remember { mutableStateOf<String?>(null) }
    var lastValidTs by remember { mutableLongStateOf(0L) }
    var overrideVerify by remember { mutableStateOf(false) }
    var showRegister by remember { mutableStateOf(false) }
    var pendingSignature by remember { mutableStateOf<FloatArray?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var selectedAmount by remember {
        mutableIntStateOf(VOLUME_OPTIONS.minByOrNull { abs(it - defaultAmountMl) } ?: 400)
    }

    // Fresh snapshot of saved cups for the (set-once) analyzer.
    val savedCupsRef = remember { mutableStateOf(savedCups) }
    savedCupsRef.value = savedCups
    val tflite = remember { OptionalTfliteModel(context) }

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
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.30f).build())
    }
    val liveLabeler = remember {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.30f).build())
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
                    analyzeLiveFrame(proxy, liveLabeler, lastAnalysisTs, savedCupsRef.value, tflite) { decision, rel ->
                        liveLabels = rel
                        isTargetLocked = decision.locked
                        isRejectedObject = decision.rejected
                        if (decision.locked) {
                            lockedSavedName = decision.savedName
                            lastValidLabel = decision.label
                            lastValidTs = System.currentTimeMillis()
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

    // Manual verify (never automatic): accept instantly if locked within 2s, else capture & scan.
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
                    labeler.process(InputImage.fromBitmap(cropped, rotation))
                        .addOnSuccessListener { labels ->
                            val pairs = labels.map { it.text to it.confidence }
                            val d = VeroSipVisionEngine.decide(cropped, pairs, savedCupsRef.value, tflite)
                            when {
                                d.locked -> { overrideVerify = false; state = VerifyState.Verified(d.label) }
                                d.rejected -> { failedAttempts++; state = VerifyState.Rejected(emptyList(), isObject = true) }
                                else -> { failedAttempts++; state = VerifyState.Rejected(VeroSipVisionEngine.relevant(pairs).map { it.first }, isObject = false) }
                            }
                        }
                        .addOnFailureListener {
                            failedAttempts++
                            state = VerifyState.Error(tr(lang, "فشل التحليل: ${it.message ?: ""}", "Analysis failed: ${it.message ?: ""}"))
                        }
                        .addOnCompleteListener {
                            if (cropped !== full) runCatching { cropped.recycle() }
                            runCatching { full.recycle() }
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
        if (isTargetLocked || (lastValidLabel != null && now - lastValidTs < 2000)) {
            overrideVerify = false
            state = VerifyState.Verified(lastValidLabel ?: "cup")
        } else {
            capture()
        }
    }

    // Capture a frame and extract its fingerprint for registration.
    fun captureForRegister() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val full = try { image.toBitmap() } catch (e: Exception) { null } finally { image.close() }
                    if (full != null) {
                        val cropped = runCatching { cropCenter(full) }.getOrDefault(full)
                        pendingSignature = runCatching { CupFingerprintMatcher.extractVisualSignature(cropped) }.getOrNull()
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
    if (showRegister) {
        var chosenName by remember { mutableStateOf("") }
        var customName by remember { mutableStateOf("") }
        val options = listOf(
            tr("عبوة مياه", "Water bottle"),
            tr("مطارة", "Flask"),
            tr("كوب أعشاب", "Herbal cup")
        )
        // Custom text takes priority over a chip when the user typed something.
        val finalName = customName.trim().ifBlank { chosenName }
        AlertDialog(
            onDismissRequest = { showRegister = false; pendingSignature = null },
            confirmButton = {
                TextButton(
                    enabled = finalName.isNotBlank() && pendingSignature != null,
                    onClick = {
                        pendingSignature?.let { onRegisterCup(finalName, selectedAmount, it) }
                        showRegister = false
                        pendingSignature = null
                        overrideVerify = false
                        state = VerifyState.Verified(finalName)
                    }
                ) { Text(tr("حفظ وتوثيق ✓", "Save & verify ✓")) }
            },
            dismissButton = { TextButton(onClick = { showRegister = false; pendingSignature = null }) { Text(tr("إلغاء", "Cancel")) } },
            title = { Text(tr("تعريف كأسي/عبوتي ⭐", "Register my cup/bottle ⭐")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("اختر اسماً سريعاً (الحجم: $selectedAmount مل):", "Pick a quick name (volume: $selectedAmount ml):"))
                    options.forEach { opt ->
                        FilterChip(
                            selected = chosenName == opt && customName.isBlank(),
                            onClick = { chosenName = opt; customName = "" },
                            label = { Text(opt) }
                        )
                    }
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        singleLine = true,
                        label = { Text(tr("أو اكتب اسماً مخصصاً (اختياري)", "Or type a custom name (optional)")) },
                        placeholder = { Text(tr("مثال: كوب المكتب", "e.g. Office mug")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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

        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { torchOn = !torchOn; camera?.cameraControl?.enableTorch(torchOn) },
            contentAlignment = Alignment.Center
        ) { Text(if (torchOn) "🔦" else "💡", fontSize = 20.sp) }

        // Register / manage buttons (top-start).
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
            val bar = when {
                lockedSavedName != null -> tr("تم التعرف على: ${lockedSavedName} ⭐", "Recognized: ${lockedSavedName} ⭐")
                liveLabels.isNotEmpty() -> tr("الرؤية: ", "Vision: ") + liveLabels.joinToString("  |  ") { "${it.first} ${(it.second * 100).toInt()}%" }
                else -> null
            }
            if (bar != null) {
                Text(
                    bar, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (lockedSavedName != null) VeroGreen.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (state is VerifyState.Analyzing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(tr("جارٍ التحقق بالذكاء الاصطناعي...", "Verifying with AI..."), color = Color.White)
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
                tr("رُصد جسم غير مسموح (طعام/إلكترونيات)! صوّر الكأس أو القارورة فقط 🚫", "A non-allowed object (food/electronics) was detected! Capture only the cup or bottle 🚫")
            } else {
                tr("لم يتم رصد كأس بوضوح — قرّب العبوة داخل الإطار", "No clear cup detected — move the bottle inside the frame") +
                    if (s.seen.isNotEmpty()) tr("\n(تم رصد: ${s.seen.joinToString("، ")})", "\n(detected: ${s.seen.joinToString(", ")})") else ""
            }
        )
        is VerifyState.Error -> MessageBox(s.message)
        else -> {}
    }

    Spacer(Modifier.height(12.dp))

    Text(tr("كم ستشرب؟ (مل)", "How much? (ml)"), color = Color.White, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VOLUME_OPTIONS.forEach { ml ->
            FilterChip(
                selected = selectedAmount == ml,
                onClick = { selectedAmount = ml },
                enabled = verified == null,
                label = { Text("$ml") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VeroCyan, selectedLabelColor = Color.Black, labelColor = Color.White)
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = { onVerifyClick() },
        enabled = state !is VerifyState.Analyzing && verified == null,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (isTargetLocked) VeroGreen else Color.White, contentColor = Color(0xFF0A1118))
    ) {
        Text(
            if (isTargetLocked) tr("توثيق الشرب ✓", "Verify drink ✓") else tr("📷 توثيق الشرب ($selectedAmount مل)", "📷 Verify drink ($selectedAmount ml)"),
            fontSize = 17.sp, fontWeight = FontWeight.Bold
        )
    }

    OutlinedButton(
        onClick = { captureForRegister() },
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
        shape = RoundedCornerShape(18.dp)
    ) { Text(tr("+ تعريف كأسي/عبوتي ⭐", "+ Register my cup/bottle ⭐"), color = Color.White) }

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
