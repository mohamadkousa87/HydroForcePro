package com.hydroforce.pro.camera

import android.content.Context
import android.graphics.Bitmap
import com.hydroforce.pro.data.SavedCupSignature
import kotlin.math.abs

/**
 * Central vision decision engine. Combines a hard blacklist, a geometric shape guard, and three
 * acceptance sources (saved fingerprint / optional TFLite model / ML Kit rules) into one result.
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
     * Merged decision: blacklist first, then (A) saved fingerprint, (B) optional TFLite model,
     * (C) ML Kit drink rule guarded by geometry.
     */
    fun decide(
        bitmap: Bitmap,
        labels: List<Pair<String, Float>>,
        savedCups: List<SavedCupSignature>,
        tflite: OptionalTfliteModel?
    ): Decision {
        if (blacklisted(labels)) return Decision(locked = false, rejected = true, label = "", savedName = null)

        // Source A: the user's saved cup/bottle fingerprint.
        CupFingerprintMatcher.findBestMatch(bitmap, savedCups)?.let {
            return Decision(locked = true, rejected = false, label = it.name, savedName = it.name)
        }
        // Source B: optional on-device TFLite model (active only when assets/verosip_vision.tflite exists).
        tflite?.classify(bitmap)?.let { cls ->
            if (cls != "negative_reject") return Decision(locked = true, rejected = false, label = cls, savedName = null)
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
 * Optional custom model reader. It auto-detects assets/verosip_vision.tflite; actual inference is
 * enabled once the TensorFlow Lite runtime dependency is added (see tools/README_VISION.md). Until
 * then [classify] returns null so the app relies on saved fingerprints + ML Kit (build stays lean).
 */
class OptionalTfliteModel(context: Context) {
    val isAvailable: Boolean = runCatching {
        context.assets.list("")?.any { it.equals("verosip_vision.tflite", ignoreCase = true) } == true
    }.getOrDefault(false)

    @Suppress("UNUSED_PARAMETER")
    fun classify(bitmap: Bitmap): String? = null
}
