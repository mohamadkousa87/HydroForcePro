package com.hydroforce.pro.camera

import android.content.Context
import android.graphics.Bitmap
import com.hydroforce.pro.data.SavedCupSignature
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

/**
 * Central vision decision engine. Combines a hard blacklist, a geometric shape guard, and three
 * acceptance sources (saved fingerprint / COCO TFLite detector / ML Kit rules) into one result.
 */
object VeroSipVisionEngine {

    /** Instant reject (food / smoking / electronics) at >= [BLACKLIST_THRESHOLD]. */
    val BLACKLIST_LABELS = setOf(
        "food", "fruit", "vegetable", "carrot", "meal", "dish", "plate", "salad", "snack", "bread",
        "meat", "pasta", "rice", "produce", "cuisine", "baked goods", "fast food", "dessert", "bowl",
        "cigarette", "electronic cigarette", "vape", "tobacco", "ashtray", "lighter", "smoke", "smoking",
        "electronic device", "gadget", "mobile phone", "telephone", "remote", "mouse", "keyboard",
        "cosmetics", "perfume", "toy", "tool"
    )
    const val BLACKLIST_THRESHOLD = 0.38f

    /** Explicit drink/container words accepted directly at >= [DRINK_THRESHOLD]. */
    val DIRECT_DRINK_LABELS = setOf(
        "water bottle", "plastic bottle", "bottle", "cup", "mug", "teacup", "coffee cup", "coffeecup",
        "drinking glass", "tumbler", "pitcher", "teapot", "water", "mineral water", "drink", "beverage",
        "tea", "coffee", "jug", "flask", "soft drink", "juice"
    )
    const val DRINK_THRESHOLD = 0.45f

    /** "glass"/"drinkware" only count WITH a liquid/drink word present. */
    val CONDITIONAL_GLASS = setOf("glass", "drinkware")
    val LIQUID_CONTEXT = setOf("liquid", "water", "fluid", "drink", "beverage", "cup", "bottle", "aqua")
    const val CONTEXT_THRESHOLD = 0.30f

    private val RELEVANT = DIRECT_DRINK_LABELS + CONDITIONAL_GLASS + LIQUID_CONTEXT + BLACKLIST_LABELS

    /** Outcome of the merged decision. */
    data class Decision(
        val locked: Boolean,
        val rejected: Boolean,
        val label: String,
        val savedName: String?
    )

    fun blacklisted(labels: List<Pair<String, Float>>): Boolean =
        labels.any { it.first.lowercase() in BLACKLIST_LABELS && it.second >= BLACKLIST_THRESHOLD }

    /** ML Kit source (ج): a direct drink word, or conditional glass paired with a liquid word. */
    fun mlDrinkLabel(labels: List<Pair<String, Float>>): String? {
        val lower = labels.map { it.first.lowercase() to it.second }
        lower.firstOrNull { it.first in DIRECT_DRINK_LABELS && it.second >= DRINK_THRESHOLD }?.let { return it.first }
        val hasGlass = lower.any { it.first in CONDITIONAL_GLASS && it.second >= DRINK_THRESHOLD }
        val hasLiquid = lower.any { it.first in LIQUID_CONTEXT && it.second >= CONTEXT_THRESHOLD }
        return if (hasGlass && hasLiquid) "glass" else null
    }

    /** Relevant labels for the diagnostic vision bar (drink words or block reasons). */
    fun relevant(labels: List<Pair<String, Float>>): List<Pair<String, Float>> =
        labels.map { it.first.lowercase() to it.second }
            .filter { it.first in RELEVANT }
            .sortedByDescending { it.second }
            .take(3)

    /**
     * Shape guard: reject flat objects (plates/ashtrays — horizontal edges dominate) and tiny/thin
     * objects (lighters/vapes that don't fill the center). Bottles/cups have balanced/vertical edges.
     */
    fun passesGeometry(bitmap: Bitmap): Boolean {
        val n = 32
        val s = Bitmap.createScaledBitmap(bitmap, n, n, true)
        val px = IntArray(n * n)
        s.getPixels(px, 0, n, 0, 0, n, n)
        if (s !== bitmap) runCatching { s.recycle() }
        val gray = FloatArray(n * n)
        for (i in px.indices) {
            val p = px[i]
            gray[i] = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
        }
        var vert = 0f
        var horiz = 0f
        var edgePx = 0
        var total = 0
        for (y in 1 until n - 1) for (x in 1 until n - 1) {
            val dx = abs(gray[y * n + (x + 1)] - gray[y * n + (x - 1)])
            val dy = abs(gray[(y + 1) * n + x] - gray[(y - 1) * n + x])
            vert += dx
            horiz += dy
            if (dx + dy > 40f) edgePx++
            total++
        }
        val vertRatio = vert / (vert + horiz + 1f)
        val coverage = edgePx.toFloat() / total.coerceAtLeast(1)
        // Flat object (horizontal edges dominate) or too few edges (small/thin) → reject.
        if (vertRatio < 0.30f) return false
        if (coverage < 0.04f) return false
        return true
    }

    /**
     * Merged decision: blacklist first, then (A) saved fingerprint, (B) COCO TFLite detector,
     * (C) ML Kit drink rule guarded by geometry.
     */
    fun decide(
        bitmap: Bitmap,
        labels: List<Pair<String, Float>>,
        savedCups: List<SavedCupSignature>,
        tflite: OptionalTfliteModel?
    ): Decision {
        if (blacklisted(labels)) return Decision(locked = false, rejected = true, label = "", savedName = null)

        // Source A: the user's saved cup/bottle fingerprint (strongest — learned from their own items).
        CupFingerprintMatcher.findBestMatch(bitmap, savedCups)?.let {
            return Decision(locked = true, rejected = false, label = it.name, savedName = it.name)
        }
        // Source B: COCO object detector (active only when assets/verosip_coco.tflite is present).
        when (val coco = tflite?.detect(bitmap)) {
            is OptionalTfliteModel.CocoResult.Reject ->
                return Decision(locked = false, rejected = true, label = coco.label, savedName = null)
            is OptionalTfliteModel.CocoResult.Accept ->
                return Decision(locked = true, rejected = false, label = coco.label, savedName = null)
            else -> {}
        }
        // Source C: ML Kit explicit rule + geometric guard.
        val ml = mlDrinkLabel(labels)
        if (ml != null && passesGeometry(bitmap)) {
            return Decision(locked = true, rejected = false, label = ml, savedName = null)
        }
        return Decision(locked = false, rejected = false, label = "", savedName = null)
    }
}

/**
 * On-device COCO object detector (SSD MobileNet v1, quantized). Auto-activates when
 * assets/verosip_coco.tflite + assets/labelmap.txt are present; if the model is missing or the
 * runtime can't load it, [detect] safely returns [CocoResult.None] and the app falls back to
 * saved fingerprints + ML Kit. See tools/README_VISION.md.
 */
class OptionalTfliteModel(context: Context) {

    sealed interface CocoResult {
        data class Accept(val label: String) : CocoResult
        data class Reject(val label: String) : CocoResult
        data object None : CocoResult
    }

    private val inputSize = 300
    private val maxDetections = 10
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    /** Container classes accepted when the box isn't plate-flat. */
    private val acceptLabels = setOf("bottle", "cup", "wine glass")
    private val acceptThreshold = 0.45f
    private val minBoxAspect = 0.65f // boxHeight / boxWidth — plates are far wider than tall.

    /** COCO classes that mean "this is not a drink" → instant reject. */
    private val rejectLabels = setOf(
        "bowl", "cell phone", "remote", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake"
    )
    private val rejectThreshold = 0.40f

    val isAvailable: Boolean = runCatching {
        labels = context.assets.open("labelmap.txt").bufferedReader().use { it.readLines() }.map { it.trim() }
        val model = loadModelFile(context, "verosip_coco.tflite")
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
        true
    }.getOrDefault(false)

    private fun loadModelFile(context: Context, name: String): MappedByteBuffer {
        val afd = context.assets.openFd(name)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    /** Runs object detection on the center-cropped frame and applies accept/reject rules. */
    fun detect(bitmap: Bitmap): CocoResult {
        val itp = interpreter ?: return CocoResult.None
        val input = runCatching { buildInput(bitmap) }.getOrNull() ?: return CocoResult.None

        // SSD MobileNet v1 outputs: locations, classes, scores, count.
        val locations = Array(1) { Array(maxDetections) { FloatArray(4) } } // ymin,xmin,ymax,xmax
        val classes = Array(1) { FloatArray(maxDetections) }
        val scores = Array(1) { FloatArray(maxDetections) }
        val count = FloatArray(1)
        val outputs = mapOf(0 to locations, 1 to classes, 2 to scores, 3 to count)

        runCatching { itp.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs) }
            .getOrElse { return CocoResult.None }

        var accept: String? = null
        val n = count[0].toInt().coerceIn(0, maxDetections)
        for (i in 0 until n) {
            val score = scores[0][i]
            if (score <= 0f) continue
            val idx = classes[0][i].toInt()
            val label = labels.getOrNull(idx)?.lowercase() ?: continue
            if (label == "???") continue
            // Reject wins over accept (immediate), per spec.
            if (label in rejectLabels && score >= rejectThreshold) return CocoResult.Reject(label)
            if (accept == null && label in acceptLabels && score >= acceptThreshold) {
                val box = locations[0][i]
                val h = box[2] - box[0]
                val w = box[3] - box[1]
                if (w > 1e-4f && h / w >= minBoxAspect) accept = label
            }
        }
        return accept?.let { CocoResult.Accept(it) } ?: CocoResult.None
    }

    private fun buildInput(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(inputSize * inputSize)
        scaled.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled !== bitmap) runCatching { scaled.recycle() }
        for (p in px) {
            buf.put(((p shr 16) and 0xFF).toByte()) // R
            buf.put(((p shr 8) and 0xFF).toByte())  // G
            buf.put((p and 0xFF).toByte())          // B
        }
        buf.rewind()
        return buf
    }
}
