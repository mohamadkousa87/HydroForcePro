package com.hydroforce.pro.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * On-device OCR (100% offline) that reads the text printed on a bottle/carton label and decides, from
 * keywords, whether it is a drink (accept) or a smoking product (reject). Lets VeroSip recognize milk
 * and juice cartons that ML Kit only labels as generic "Food".
 *
 * [read] blocks briefly (bounded by a timeout) — call it on a BACKGROUND thread, never the main one.
 */
class VeroSipOcr {

    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class OcrOutcome(val accept: String?, val reject: String?)

    fun read(bitmap: Bitmap, rotationDegrees: Int = 0): OcrOutcome {
        return runCatching {
            val img = InputImage.fromBitmap(bitmap, rotationDegrees)
            val text = Tasks.await(client.process(img), 800, TimeUnit.MILLISECONDS)
            val t = text.text.lowercase()
            if (t.isBlank()) return@runCatching OcrOutcome(null, null)
            REJECT_KEYWORDS.firstOrNull { t.contains(it) }?.let { return@runCatching OcrOutcome(null, it) }
            val accept = DRINK_KEYWORDS.firstOrNull { t.contains(it) }
            OcrOutcome(accept, null)
        }.getOrDefault(OcrOutcome(null, null))
    }

    companion object {
        /** Reading any of these on the object → accept it as a drink pack. */
        val DRINK_KEYWORDS = listOf(
            "water", "aqua", "milk", "laban", "leben", "ayran", "yogurt", "yoghurt", "dairy", "juice",
            "drink", "beverage", "fresh", "natural", "mineral", "pure", "almarai", "rawabi", "masafi",
            "arwa", "aquafina", "evian", "mai dubai", "al ain", "nadec", "marmum", "lacnor", "vimto",
            "tea", "coffee", "liter", "litre", "fl oz", "330ml", "500ml", "250ml", "200ml", "180ml",
            "1.5l", "ph ", " ml"
        )

        /** Reading any of these → reject (smoking products). */
        val REJECT_KEYWORDS = listOf(
            "nicotine", "tobacco", "vape", "smoking", "cigarette", "cigar", "puff", "ohm"
        )
    }
}
