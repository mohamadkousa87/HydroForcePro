package com.hydroforce.pro.camera

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional Cloud AI verification using the user's OWN Gemini API key (BYOK). Nothing is uploaded
 * unless the user enables it and provides a key. Robust against hardware bitmaps, Markdown-wrapped
 * JSON, and 404 model names; surfaces the real error on failure.
 *
 * [verify] and [verifyKey] block on the network — call them on a BACKGROUND thread.
 */
object CloudVisionVerifier {

    sealed interface Result {
        data class Positive(val label: String) : Result
        /** The cloud looked and it is NOT a drink (e.g. ashtray/furniture) — not an error. */
        data class Negative(val item: String) : Result
        /** Disabled, offline, bad key, or any error — with a concrete reason. */
        data class Unavailable(val reason: String) : Result
    }

    sealed interface KeyStatus {
        data object Ok : KeyStatus
        data class Invalid(val detail: String) : KeyStatus
        data object NoNetwork : KeyStatus
    }

    private val PREFERRED = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")

    @Volatile
    private var cachedModel: String? = null

    private const val PROMPT =
        "Look closely at this image. Is the main object a real beverage container or drink " +
            "(water glass, cup, mug, water bottle, milk/laban/juice carton)? Note: an ashtray, empty " +
            "bowl, plate, furniture, or electronics is NOT a drink. Reply ONLY with valid JSON: " +
            "{\"is_drink\": true or false, \"item_ar\": \"اسم الشيء بالعربية\"}"

    /** Extract an AIza… key even if the user pasted a whole URL; else trimmed, whitespace-stripped. */
    private fun clean(raw: String): String =
        Regex("AIza[0-9A-Za-z\\-_]{30,}").find(raw)?.value ?: raw.trim().replace("\\s+".toRegex(), "")

    fun verify(bitmap: Bitmap, rawKey: String): Result {
        val key = clean(rawKey)
        if (key.isBlank()) return Result.Unavailable("empty key")

        // Hardware bitmaps throw on copy/compress — convert to a software bitmap first.
        val safe = try {
            if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        } catch (e: Exception) {
            return Result.Unavailable("BitmapError: ${e.message?.take(60)}")
        }
        val jpeg = try {
            compress(safe)
        } catch (e: Exception) {
            if (safe !== bitmap) runCatching { safe.recycle() }
            return Result.Unavailable("BitmapError: ${e.message?.take(60)}")
        }
        if (safe !== bitmap) runCatching { safe.recycle() }

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

        val models = (listOf(resolveModel(key)) + PREFERRED).distinct()
        var lastError = "unknown"
        for (model in models) {
            val res = post("$model:generateContent", key, body) ?: return Result.Unavailable("Timeout/Network")
            val (code, resp) = res
            when {
                code == 404 -> { cachedModel = null; lastError = "HTTP 404 ($model)"; continue }
                code !in 200..299 -> return Result.Unavailable("HTTP $code: ${parseError(resp).take(60)}")
                else -> {
                    val (isDrink, item) = readResult(resp)
                    return when (isDrink) {
                        true -> Result.Positive(item.ifBlank { "مشروب" })
                        false -> Result.Negative(item.ifBlank { "غير مشروب" })
                        null -> Result.Unavailable("bad response")
                    }
                }
            }
        }
        return Result.Unavailable(lastError)
    }

    /** Key check via a lightweight GET on the models list (no body, never 404s on a model name). */
    fun verifyKey(rawKey: String): KeyStatus {
        val key = clean(rawKey)
        if (key.isBlank()) return KeyStatus.Invalid("المفتاح فارغ / empty key")
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                cachedModel = pickModel(resp) // remember a vision model for image checks
                KeyStatus.Ok
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                KeyStatus.Invalid(parseError(err).ifBlank { "HTTP $code" })
            }
        } catch (e: java.net.UnknownHostException) {
            KeyStatus.NoNetwork
        } catch (e: IOException) {
            KeyStatus.NoNetwork
        } catch (e: Exception) {
            KeyStatus.Invalid(e.message ?: "error")
        }
    }

    /** Return a cached/known model, else query the account's model list, else the first preferred. */
    private fun resolveModel(key: String): String {
        cachedModel?.let { return it }
        val res = post2("models", key, null, "GET")
        val model = res?.takeIf { it.first in 200..299 }?.let { pickModel(it.second) }
        if (model != null) cachedModel = model
        return model ?: PREFERRED.first()
    }

    /** From a GET /models response, pick the best flash model that supports generateContent. */
    private fun pickModel(resp: String): String? = runCatching {
        val arr = JSONObject(resp).getJSONArray("models")
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").removePrefix("models/")
            val methods = o.optJSONArray("supportedGenerationMethods")
            val supports = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (supports && name.isNotBlank()) names += name
        }
        PREFERRED.firstOrNull { it in names }
            ?: names.firstOrNull { it.contains("gemini") && it.contains("flash") }
            ?: names.firstOrNull { it.contains("gemini") }
    }.getOrNull()

    /** Returns (httpCode, body) or null on a network-level failure. */
    private fun post(path: String, key: String, body: String): Pair<Int, String>? =
        post2(path, key, body, "POST")

    private fun post2(path: String, key: String, body: String?, method: String): Pair<Int, String>? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/$path?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 12000
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            code to text
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            -1 to (e.message ?: "")
        }
    }

    private fun parseText(resp: String): String = runCatching {
        JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }.getOrDefault("")

    private fun parseError(json: String): String = runCatching {
        JSONObject(json).getJSONObject("error").getString("message")
    }.getOrDefault("")

    /** Flexible parse of the model text (may be wrapped in ```json fences or extra prose). */
    private fun readResult(resp: String): Pair<Boolean?, String> {
        val text = parseText(resp)
        val slice = text.substringAfter('{', "").substringBeforeLast('}', "")
        if (slice.isNotBlank()) {
            val json = runCatching { JSONObject("{$slice}") }.getOrNull()
            if (json != null && json.has("is_drink")) {
                val item = json.optString("item_ar", json.optString("item", ""))
                return json.optBoolean("is_drink") to item
            }
        }
        val lo = text.lowercase()
        val b = when {
            Regex("\"?is_drink\"?\\s*:\\s*true").containsMatchIn(lo) -> true
            Regex("\"?is_drink\"?\\s*:\\s*false").containsMatchIn(lo) -> false
            lo.contains("yes") && !lo.contains("no") -> true
            lo.contains("no") && !lo.contains("yes") -> false
            else -> null
        }
        return b to ""
    }

    /** Hardware-safe compress to a small JPEG (<=512px, quality 75). */
    private fun compress(bitmap: Bitmap): ByteArray {
        val max = 512
        val scale = minOf(1f, max.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        if (scaled !== bitmap) runCatching { scaled.recycle() }
        return out.toByteArray()
    }
}
