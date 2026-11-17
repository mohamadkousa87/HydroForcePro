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

/**
 * Seamless, permissive local vision engine. A frame locks (green) as soon as ANY drink/container
 * signal appears — a saved barcode/keyword, a read label, a COCO container, or an ML Kit hint — with
 * only a narrow high-confidence block for smoking/electronics. No fragile color fingerprint or strict
 * geometry gates; those caused real cups/cartons to be rejected and backgrounds to be false-matched.
 */
object VeroSipVisionEngine {

    /** Strong acceptance — any of these (ML Kit) at >= [ACCEPT_THRESHOLD] locks the frame. */
    val ACCEPT_HINTS = setOf(
        "water", "bottle", "water bottle", "plastic bottle", "glass", "drinking glass", "cup", "mug",
        "teacup", "coffee cup", "coffeecup", "drink", "liquid", "fluid", "beverage", "drinkware",
        "glassware", "pitcher", "tumbler", "teapot", "tea", "coffee", "jug", "flask",
        "milk", "dairy", "dairy product", "yogurt", "ayran", "juice", "soft drink", "aqua"
    )
    const val ACCEPT_THRESHOLD = 0.28f

    /** Ambiguous words that only count WITH a liquid/drink word in the same frame (baskets, boxes…). */
    val CONDITIONAL_HINTS = setOf("tableware", "container", "cylinder", "plastic", "packaged goods", "carton")
    val LIQUID_CONTEXT = setOf("water", "liquid", "drink", "beverage", "milk", "aqua", "juice", "dairy", "fluid")

    /** Instant block — smoking / electronics / furniture / baskets / fabrics / surfaces, at >= 0.38. */
    val BLOCK_OBJECTS = setOf(
        "basket", "hamper", "laundry", "wicker", "wood", "plywood", "hardwood", "furniture", "chair",
        "table", "couch", "sofa", "pillow", "cushion", "bed", "textile", "fabric", "linen", "towel",
        "cloth", "floor", "flooring", "tile", "wall", "door", "window", "television", "monitor",
        "laptop", "keyboard", "mouse", "mobile phone", "cell phone", "remote", "cigarette",
        "electronic cigarette", "vape", "tobacco", "ashtray", "lighter", "shoe", "bag", "handbag"
    )
    const val BLOCK_THRESHOLD = 0.38f

    private val RELEVANT = ACCEPT_HINTS + CONDITIONAL_HINTS + BLOCK_OBJECTS

    /** Outcome of the merged decision. */
    data class Decision(
        val locked: Boolean,
        val rejected: Boolean,
        val label: String,
        val savedName: String?,
        /** Non-null when the lock came from reading the bottle/carton label (OCR). */
        val ocrLabel: String? = null
    )

    /** Relevant labels for the diagnostic vision bar. */
    fun relevant(labels: List<Pair<String, Float>>): List<Pair<String, Float>> =
        labels.map { it.first.lowercase() to it.second }
            .filter { it.first in RELEVANT }
            .sortedByDescending { it.second }
            .take(3)

    /** A saved cup whose stored barcode/keyword matches this frame's barcode or read label text. */
    fun matchSaved(savedCups: List<SavedCupSignature>, barcode: String?, ocrText: String?): SavedCupSignature? {
        if (savedCups.isEmpty()) return null
        val code = barcode?.trim()?.lowercase()
        val text = ocrText?.lowercase()
        for (cup in savedCups) {
            val key = cup.barcodeOrKeyword.trim().lowercase()
            if (key.isBlank()) continue
            if (code != null && key == code) return cup
            if (text != null && key.length >= 3 && text.contains(key)) return cup
        }
        return null
    }

    /**
     * Merged decision (seamless order):
     *  1) OCR smoking text → reject;
     *  2) saved cup matched by barcode/keyword → lock (⭐);
     *  3) any barcode → lock (a scanned pack);
     *  4) OCR drink keyword → lock (label read);
     *  5) COCO container → lock;
     *  6) ML Kit hint >= 0.28 → lock;
     *  7) narrow block object >= 0.58 (no barcode / no drink text) → reject;
     *  else scanning.
     */
    fun decide(
        bitmap: Bitmap,
        labels: List<Pair<String, Float>>,
        savedCups: List<SavedCupSignature>,
        tflite: OptionalTfliteModel?,
        ocr: VeroSipOcr? = null,
        barcode: VeroSipBarcode? = null,
        useHeavy: Boolean = false,
        cloudMode: Boolean = false
    ): Decision {
        // Cloud mode: the cloud is the judge (at the verify tap). The live preview only locks for the
        // user's own saved cups (instant & free); everything else stays "scanning" until the cloud check.
        if (cloudMode) {
            val code = if (useHeavy && barcode != null) barcode.read(bitmap) else null
            val ocrText = if (useHeavy && ocr != null) ocr.read(bitmap).rawText else null
            matchSaved(savedCups, code, ocrText)?.let {
                return Decision(locked = true, rejected = false, label = it.name, savedName = it.name)
            }
            return Decision(false, false, "", null)
        }

        val lower = labels.map { it.first.lowercase() to it.second }

        // Heavy, high-value signals (barcode + OCR) — only when requested (throttled live, full on verify).
        val code = if (useHeavy && barcode != null) barcode.read(bitmap) else null
        val ocrOutcome = if (useHeavy && ocr != null) ocr.read(bitmap) else null

        // 1. Smoking text on the pack → reject (wins over everything).
        ocrOutcome?.reject?.let { return Decision(false, true, it, null) }

        // 2. One of the user's saved favorites (by barcode or printed keyword).
        matchSaved(savedCups, code, ocrOutcome?.rawText)?.let {
            return Decision(locked = true, rejected = false, label = it.name, savedName = it.name)
        }
        // 3. Any barcode in frame → it's a packaged product; accept as a drink pack.
        if (code != null) return Decision(locked = true, rejected = false, label = "barcode", savedName = null)
        // 4. A drink word read on the label.
        ocrOutcome?.accept?.let { return Decision(locked = true, rejected = false, label = it, savedName = null, ocrLabel = it) }
        // 5. COCO container (bottle/cup/wine glass). Ignore COCO reject here (permissive mode).
        if (tflite?.detect(bitmap) is OptionalTfliteModel.CocoResult.Accept) {
            return Decision(locked = true, rejected = false, label = "cup", savedName = null)
        }
        // 6a. Strong ML Kit hint.
        lower.firstOrNull { it.first in ACCEPT_HINTS && it.second >= ACCEPT_THRESHOLD }?.let {
            return Decision(locked = true, rejected = false, label = it.first, savedName = null)
        }
        // 6b. Ambiguous word (tableware/container/plastic…) only with a liquid/drink word present.
        val hasLiquid = lower.any { it.first in LIQUID_CONTEXT && it.second >= ACCEPT_THRESHOLD }
        lower.firstOrNull { it.first in CONDITIONAL_HINTS && it.second >= ACCEPT_THRESHOLD }?.let {
            if (hasLiquid) return Decision(locked = true, rejected = false, label = it.first, savedName = null)
        }
        // 7. Block furniture/baskets/fabrics/electronics/smoking when nothing drink-like is present.
        val hasDrinkText = ocrOutcome?.accept != null
        if (code == null && !hasDrinkText &&
            lower.any { it.first in BLOCK_OBJECTS && it.second >= BLOCK_THRESHOLD }
        ) {
            return Decision(false, true, "", null)
        }
        return Decision(false, false, "", null)
    }
}

/**
 * On-device COCO object detector (SSD MobileNet v1, quantized). Auto-activates when
 * assets/verosip_coco.tflite + assets/labelmap.txt are present; if the model is missing or the
 * runtime can't load it, [detect] safely returns [CocoResult.None]. See tools/README_VISION.md.
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
    private val acceptThreshold = 0.30f
    private val minBoxAspect = 0.55f // boxHeight / boxWidth — plates are far wider than tall.

    /** COCO classes that mean "this is not a drink" → instant reject. */
    private val rejectLabels = setOf("cell phone", "remote", "laptop", "keyboard", "mouse")
    private val rejectThreshold = 0.50f

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
