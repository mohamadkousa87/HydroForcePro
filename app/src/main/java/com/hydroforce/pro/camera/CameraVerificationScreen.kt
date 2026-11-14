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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.hydroforce.pro.ui.HydroDanger
import com.hydroforce.pro.ui.LocalLanguage
import com.hydroforce.pro.ui.tr
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

// ---- Three-tier strict filtering ----
/** Instant reject (food / smoking / electronics) at >= [BLACKLIST_THRESHOLD]. */
val BLACKLIST_LABELS = setOf(
    "food", "fruit", "vegetable", "carrot", "meal", "dish", "plate", "salad", "snack", "bread",
    "meat", "pasta", "rice", "produce", "cuisine", "baked goods", "fast food", "dessert", "bowl",
    "cigarette", "electronic cigarette", "vape", "tobacco", "ashtray", "lighter", "smoke", "smoking",
    "electronic device", "gadget", "mobile phone", "telephone", "remote", "mouse", "keyboard",
    "cosmetics", "perfume", "toy", "tool"
)
const val BLACKLIST_THRESHOLD = 0.38f

/** Explicit drink/container words accepted directly at >= [CONFIDENCE_THRESHOLD]. */
val DIRECT_DRINK_LABELS = setOf(
    "water bottle", "plastic bottle", "bottle", "cup", "mug", "teacup", "coffee cup", "coffeecup",
    "drinking glass", "tumbler", "pitcher", "teapot", "water", "mineral water", "drink", "beverage",
    "tea", "coffee", "jug", "flask", "soft drink", "juice"
)
const val CONFIDENCE_THRESHOLD = 0.45f

/** "glass"/"drinkware" only count WITH a liquid/drink word present (blocks glass lighters/vapes). */
val CONDITIONAL_GLASS = setOf("glass", "drinkware")
val LIQUID_CONTEXT = setOf("liquid", "water", "fluid", "drink", "beverage", "cup", "bottle", "aqua")
const val CONTEXT_THRESHOLD = 0.30f

private val RELEVANT_LABELS = DIRECT_DRINK_LABELS + CONDITIONAL_GLASS + LIQUID_CONTEXT + BLACKLIST_LABELS

private val VeroCyan = Color(0xFF00E5FF)
private val VeroGreen = Color(0xFF00E676)
private val VeroRed = Color(0xFFFF5252)
private val VeroOrange = Color(0xFFFF8F3A)
private val RoyalBlue = Color(0xFF003B99)

private val VOLUME_OPTIONS = listOf(100, 200, 300, 400, 500)

private sealed interface Outcome {
    data object Rejected : Outcome          // food / smoking / electronics
    data class Drink(val label: String) : Outcome
    data class None(val seen: List<String>) : Outcome
}

private fun evaluate(labels: List<Pair<String, Float>>): Outcome {
    val lower = labels.map { it.first.lowercase() to it.second }
    if (lower.any { it.first in BLACKLIST_LABELS && it.second >= BLACKLIST_THRESHOLD }) return Outcome.Rejected
    lower.firstOrNull { it.first in DIRECT_DRINK_LABELS && it.second >= CONFIDENCE_THRESHOLD }
        ?.let { return Outcome.Drink(it.first) }
    val hasGlass = lower.any { it.first in CONDITIONAL_GLASS && it.second >= CONFIDENCE_THRESHOLD }
    val hasLiquid = lower.any { it.first in LIQUID_CONTEXT && it.second >= CONTEXT_THRESHOLD }
    if (hasGlass && hasLiquid) return Outcome.Drink("glass")
    return Outcome.None(lower.filter { it.first in RELEVANT_LABELS }.sortedByDescending { it.second }.take(3).map { it.first })
}

/** Center ROI crop: keep the middle [fraction] of the frame to ignore the background. */
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
    onLabels: (List<Pair<String, Float>>) -> Unit
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
        .addOnSuccessListener { labels -> onLabels(labels.map { it.text to it.confidence }) }
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
            CameraContent(defaultAmountMl = defaultAmountMl, onVerified = onVerified)
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
private fun CameraContent(defaultAmountMl: Int, onVerified: (Int, Boolean) -> Unit) {
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
    var lastValidLabel by remember { mutableStateOf<String?>(null) }
    var lastValidTs by remember { mutableLongStateOf(0L) }
    var overrideVerify by remember { mutableStateOf(false) }
    var selectedAmount by remember {
        mutableIntStateOf(VOLUME_OPTIONS.minByOrNull { abs(it - defaultAmountMl) } ?: 400)
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    // Low base threshold (0.30) so we read the top labels of the cropped frame.
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
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                    analyzeLiveFrame(proxy, liveLabeler, lastAnalysisTs) { labels ->
                        // Live analysis NEVER auto-records — it only drives the visual HUD state.
                        liveLabels = labels.map { it.first.lowercase() to it.second }
                            .filter { it.first in RELEVANT_LABELS }
                            .sortedByDescending { it.second }
                            .take(3)
                        when (val o = evaluate(labels)) {
                            is Outcome.Drink -> {
                                isTargetLocked = true; isRejectedObject = false
                                lastValidLabel = o.label; lastValidTs = System.currentTimeMillis()
                            }
                            is Outcome.Rejected -> { isTargetLocked = false; isRejectedObject = true }
                            is Outcome.None -> { isTargetLocked = false; isRejectedObject = false }
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
                            when (val outcome = evaluate(labels.map { it.text to it.confidence })) {
                                is Outcome.Drink -> { overrideVerify = false; state = VerifyState.Verified(outcome.label) }
                                is Outcome.Rejected -> { failedAttempts++; state = VerifyState.Rejected(emptyList(), isObject = true) }
                                is Outcome.None -> { failedAttempts++; state = VerifyState.Rejected(outcome.seen, isObject = false) }
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

    // Manual verify: accept instantly if the live scan locked on within the last 2s; else capture & scan.
    fun onVerifyClick() {
        val now = System.currentTimeMillis()
        if (isTargetLocked || (lastValidLabel != null && now - lastValidTs < 2000)) {
            overrideVerify = false
            state = VerifyState.Verified(lastValidLabel ?: "cup")
        } else {
            capture()
        }
    }

    // Pulsing glow for the locked state.
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(380.dp)
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

        // Viewfinder HUD: brackets + central glassy water drop, in 3 states.
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
            // Glow pulse around brackets when locked.
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

            // Central water drop watermark.
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
                // green check inside the drop
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
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(if (torchOn) "🔦" else "💡", fontSize = 20.sp)
        }

        if (verified == null && liveLabels.isNotEmpty()) {
            val visionText = liveLabels.joinToString("  |  ") { "${it.first} ${(it.second * 100).toInt()}%" }
            Text(
                tr("الرؤية: ", "Vision: ") + visionText,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (state is VerifyState.Analyzing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
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
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(VeroGreen),
                    contentAlignment = Alignment.Center
                ) {
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
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VOLUME_OPTIONS.forEach { ml ->
            FilterChip(
                selected = selectedAmount == ml,
                onClick = { selectedAmount = ml },
                enabled = verified == null,
                label = { Text("$ml") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VeroCyan,
                    selectedLabelColor = Color.Black,
                    labelColor = Color.White
                )
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // Main button is ALWAYS the camera verify action.
    Button(
        onClick = { onVerifyClick() },
        enabled = state !is VerifyState.Analyzing && verified == null,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTargetLocked) VeroGreen else Color.White,
            contentColor = Color(0xFF0A1118)
        )
    ) {
        Text(
            if (isTargetLocked) tr("توثيق الشرب ✓", "Verify drink ✓") else tr("📷 توثيق الشرب ($selectedAmount مل)", "📷 Verify drink ($selectedAmount ml)"),
            fontSize = 17.sp, fontWeight = FontWeight.Bold
        )
    }

    // Emergency promise bypass only after 3 failed manual attempts, as a separate secondary button.
    if (failedAttempts >= 3 && verified == null) {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { overrideVerify = true; state = VerifyState.Verified("promise") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VeroOrange, contentColor = Color.White)
        ) {
            Text(tr("تأكيد بالتعهد 🤝 (تجاوز)", "Confirm by promise 🤝 (bypass)"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
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
