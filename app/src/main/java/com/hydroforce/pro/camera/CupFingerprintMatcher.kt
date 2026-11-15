package com.hydroforce.pro.camera

import android.graphics.Bitmap
import com.hydroforce.pro.data.SavedCupSignature
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * On-device visual fingerprint of a cup/bottle: a compact 64-value L2-normalized vector mixing
 * a 4x4 color/brightness grid (transparency, cap color, cup color) with a 4x4 edge-density grid
 * (vertical/horizontal gradients that capture the cylindrical shape). Matched by cosine similarity.
 */
object CupFingerprintMatcher {

    const val MATCH_THRESHOLD = 0.88f
    private const val N = 32          // working resolution
    const val VECTOR_SIZE = 64        // 48 color (4x4x3) + 16 edge (4x4)

    fun extractVisualSignature(centerCroppedBitmap: Bitmap): FloatArray {
        val s = Bitmap.createScaledBitmap(centerCroppedBitmap, N, N, true)
        val px = IntArray(N * N)
        s.getPixels(px, 0, N, 0, 0, N, N)
        if (s !== centerCroppedBitmap) runCatching { s.recycle() }

        val gray = FloatArray(N * N)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val feat = FloatArray(VECTOR_SIZE)
        val cell = N / 4 // 8

        // 4x4 color grid → 48 values (R,G,B averages per cell, 0..1).
        for (gy in 0 until 4) for (gx in 0 until 4) {
            var sr = 0L; var sg = 0L; var sb = 0L
            for (y in gy * cell until (gy + 1) * cell) for (x in gx * cell until (gx + 1) * cell) {
                val p = px[y * N + x]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            val n = (cell * cell).toFloat()
            val idx = (gy * 4 + gx) * 3
            feat[idx] = sr / n / 255f
            feat[idx + 1] = sg / n / 255f
            feat[idx + 2] = sb / n / 255f
        }

        // 4x4 edge-density grid → 16 values (sum of |dx|+|dy| gradients per cell, normalized).
        for (gy in 0 until 4) for (gx in 0 until 4) {
            var sum = 0f
            for (y in gy * cell until (gy + 1) * cell) for (x in gx * cell until (gx + 1) * cell) {
                if (x in 1 until N - 1 && y in 1 until N - 1) {
                    val dx = gray[y * N + (x + 1)] - gray[y * N + (x - 1)]
                    val dy = gray[(y + 1) * N + x] - gray[(y - 1) * N + x]
                    sum += abs(dx) + abs(dy)
                }
            }
            feat[48 + gy * 4 + gx] = sum / (cell * cell) / 255f
        }

        // L2 normalize.
        var norm = 0f
        for (v in feat) norm += v * v
        norm = sqrt(norm)
        if (norm > 1e-6f) for (i in feat.indices) feat[i] /= norm
        return feat
    }

    fun serialize(vector: FloatArray): String = vector.joinToString(",")

    fun parse(csv: String): FloatArray =
        csv.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()

    /** Cosine similarity of two L2-normalized vectors = their dot product. */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    /** Best saved cup whose cosine similarity to [bitmap] clears [MATCH_THRESHOLD], or null. */
    fun findBestMatch(bitmap: Bitmap, savedCups: List<SavedCupSignature>): SavedCupSignature? {
        if (savedCups.isEmpty()) return null
        val sig = extractVisualSignature(bitmap)
        var best: SavedCupSignature? = null
        var bestScore = MATCH_THRESHOLD
        for (cup in savedCups) {
            val score = cosine(sig, parse(cup.featureVectorCsv))
            if (score >= bestScore) {
                bestScore = score
                best = cup
            }
        }
        return best
    }
}
