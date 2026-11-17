package com.hydroforce.pro.camera

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional Cloud AI verification using the user's OWN Gemini API key (BYOK). Model selection is FULLY
 * dynamic — it queries the account's own model list (GET /models) and uses whatever generateContent
 * models exist, newest flash first. This survives Google retiring version numbers (1.5/2.0/…) without
 * any code change. Robust against hardware bitmaps and Markdown-wrapped JSON; surfaces real errors.
 *
 * [verify] and [verifyKey] block on the network — call them on a BACKGROUND thread.
 */
object CloudVisionVerifier {

    sealed interface Result {
        data class Positive(val label: String) : Result
        /** The cloud looked and it is NOT a drink (e.g. ashtray/furniture) — not an error. */
        data class Negative(val item: String) : Result
        /** All candidate models were overloaded (503/429/500) — caller should use the local engine. */
        data class Overloaded(val reason: String) : Result
        /** Disabled, offline, bad key, or any error — with a concrete reason. */
        data class Unavailable(val reason: String) : Result
    }

    sealed interface KeyStatus {
        data object Ok : KeyStatus
        data class Invalid(val detail: String) : KeyStatus
        data object NoNetwork : KeyStatus
    }

    /** Model names (short, no "models/") excluded — not image/text chat models. */
    private val EXCLUDE = listOf("embedding", "imagen", "tts", "aqa", "audio", "veo", "learnlm")

    /** Cached, ranked list of callable model names (as returned by the server, incl. "models/"). */
    @Volatile
    private var cachedModels: List<String>? = null

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

        val ordered = discoverModels(key).ifEmpty {
            listOf("models/gemini-flash-latest", "models/gemini-2.5-flash")
        }
        var lastError = "unknown"
        var sawOverload = false
        for (name in ordered) {
            val modelPath = if (name.startsWith("models/")) name else "models/$name"
            val res = post("$modelPath:generateContent", key, body) ?: return Result.Unavailable("Timeout/Network")
            val (code, resp) = res
            when {
                // Missing model name → skip quietly.
                code == 404 || code == 400 -> { lastError = "HTTP $code (${name.removePrefix("models/")})"; continue }
                // Overloaded / rate-limited / server error → try the next model instantly.
                code == 503 || code == 429 || code == 500 -> { sawOverload = true; lastError = "HTTP $code"; continue }
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
        cachedModels = null // force a fresh discovery next time
        // Every candidate was overloaded → let the caller fall back to the local engine.
        return if (sawOverload) Result.Overloaded(lastError) else Result.Unavailable(lastError)
    }

    /** Key check via GET /models (no body, never 404s on a model name) + caches the ranked list. */
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
                cachedModels = rankModels(resp)
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

    private fun discoverModels(key: String): List<String> {
        cachedModels?.let { if (it.isNotEmpty()) return it }
        val res = post2("models", key, null, "GET") ?: return emptyList()
        if (res.first !in 200..299) return emptyList()
        return rankModels(res.second).also { if (it.isNotEmpty()) cachedModels = it }
    }

    /** Rank generateContent models: stable *-latest aliases, then newest flash, then other gemini. */
    private fun rankModels(resp: String): List<String> = runCatching {
        val arr = JSONObject(resp).getJSONArray("models")
        val cand = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name")
            val short = name.removePrefix("models/").lowercase()
            if (short.isBlank()) continue
            val methods = o.optJSONArray("supportedGenerationMethods")
            val supports = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (!supports) continue
            if (EXCLUDE.any { short.contains(it) }) continue
            cand += name
        }
        fun ver(n: String): Double =
            Regex("gemini-([0-9]+(?:\\.[0-9]+)?)").find(n.lowercase())?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val stable = cand.filter {
            val s = it.lowercase()
            s.endsWith("gemini-flash-latest") || s.endsWith("gemini-flash-lite-latest")
        }.sortedBy { if (it.lowercase().contains("lite")) 1 else 0 }
        // Flash by newest version; within a version, full flash before flash-lite (lite is the quick backup).
        val flash = cand.filter { it.lowercase().contains("flash") && it !in stable }
            .sortedWith(compareByDescending<String> { ver(it) }.thenBy { if (it.lowercase().contains("lite")) 1 else 0 })
        // Remaining gemini models (e.g. pro) as a last resort.
        val others = cand.filter { it !in stable && it !in flash }.sortedByDescending { ver(it) }
        (stable + flash + others).distinct()
    }.getOrDefault(emptyList())

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
