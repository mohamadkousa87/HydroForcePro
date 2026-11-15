package com.hydroforce.pro.camera

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional Cloud AI verification using the user's OWN Gemini API key (BYOK — Bring Your Own Key).
 * Nothing is uploaded unless the user enables it and provides a key. On any error / no network, the
 * caller falls back to the local engine, so the user is never blocked.
 *
 * [verify] blocks on the network — call it on a BACKGROUND thread.
 */
object CloudVisionVerifier {

    sealed interface Result {
        data class Positive(val label: String) : Result
        data object Negative : Result
        /** Disabled, offline, bad key, or any error → caller should use the local engine. */
        data object Unavailable : Result
    }

    private const val MODEL = "gemini-2.5-flash"
    private const val PROMPT =
        "Look at this photo. Reply with exactly one word: YES if it clearly shows a drink, water, " +
            "milk, laban, ayran, juice, tea, coffee, or a cup / glass / mug / drinking bottle / drink " +
            "carton. Reply NO for anything else (food plate, phone, vape, cigarette, empty scene)."

    fun verify(bitmap: Bitmap, apiKey: String): Result {
        if (apiKey.isBlank()) return Result.Unavailable
        return runCatching {
            val jpeg = compress(bitmap)
            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray()
                        .put(JSONObject().put("text", PROMPT))
                        .put(JSONObject().put("inline_data", JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", b64))))
                }))
            }.toString()

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return Result.Unavailable
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val text = parseText(resp).uppercase()
            when {
                text.contains("YES") -> Result.Positive("cloud")
                text.contains("NO") -> Result.Negative
                else -> Result.Unavailable
            }
        }.getOrDefault(Result.Unavailable)
    }

    private fun parseText(resp: String): String = runCatching {
        JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }.getOrDefault("")

    /** Compress to a small JPEG (~30 KB target) to keep the upload tiny and fast. */
    private fun compress(bitmap: Bitmap): ByteArray {
        val max = 512
        val scale = minOf(1f, max.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
        if (scaled !== bitmap) runCatching { scaled.recycle() }
        return out.toByteArray()
    }
}
