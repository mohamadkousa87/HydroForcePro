package com.hydroforce.pro.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit

/**
 * On-device barcode reader (100% offline). Reading a barcode on a bottle/carton is the most reliable
 * recognition signal — the same product always yields the same code regardless of background/lighting.
 *
 * [read] blocks briefly (bounded by a timeout) — call it on a BACKGROUND thread, never the main one.
 */
class VeroSipBarcode {

    private val scanner = BarcodeScanning.getClient()

    /** The first non-blank barcode value in the frame, or null. */
    fun read(bitmap: Bitmap, rotationDegrees: Int = 0): String? {
        return runCatching {
            val img = InputImage.fromBitmap(bitmap, rotationDegrees)
            val codes = Tasks.await(scanner.process(img), 700, TimeUnit.MILLISECONDS)
            codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
        }.getOrNull()
    }
}
