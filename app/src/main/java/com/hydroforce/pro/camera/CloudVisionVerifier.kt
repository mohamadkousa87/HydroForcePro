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
 * unless the user enables it and provides a key. On any error / no network the caller informs & stops
 * (no silent local fallback), per the chosen design.
 *
 * [verify] and [verifyKey] block on the network — call them on a BACKGROUND thread.
 */
object CloudVisionVerifier {

    sealed interface Result {
        data class Positive(val label: String) : Result
        data object Negative : Result
        /** Disabled, offline, bad key, or any error. */
        data object Unavailable : Result
    }

    sealed interface KeyStatus {
        data object Ok : KeyStatus
        data class Invalid(val detail: String) : KeyStatus
        data object NoNetwork : KeyStatus
    }

    /** Modern model fallback chain — try newest first, fall back on 404. */
    private val MODELS = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-flash-latest")

    private const val PROMPT =
        "Is there a real drink, water, milk, laban, ayran, juice, tea, coffee, or a cup / glass / mug / " +
            "drinking bottle / drink carton in this photo (something a person actually drinks from)? " +
            "Furniture, a room, a basket, clothes, a phone, or any non-drinking object is NOT a drink. " +
            "Reply with ONLY compact JSON: {\"is_drink\": true/false, \"label\": \"short label\"}"

    /** Extract an AIza… key even if the user pasted a whole URL; else trimmed, whitespace-stripped. */
    private fun clean(raw: String): String =
        Regex("AIza[0-9A-Za-z\\-_]{30,}").find(raw)?.value ?: raw.trim().replace("\\s+".toRegex(), "")

    fun verify(bitmap: Bitmap, rawKey: String): Result {
        val key = clean(rawKey)
        if (key.isBlank()) return Result.Unavailable
        val jpeg = runCatching { compress(bitmap) }.getOrNull() ?: return Result.Unavailable
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

        for (model in MODELS) {
            val (code, resp) = post(model, key, body) ?: return Result.Unavailable // null = network error
            when {
                code == 404 -> continue // try next model
                code !in 200..299 -> return Result.Unavailable
                else -> {
                    val text = parseText(resp)
                    return when (readIsDrink(text)) {
                        true -> Result.Positive(readLabel(text))
                        false -> Result.Negative
                        null -> Result.Unavailable
                    }
                }
            }
        }
        return Result.Unavailable
    }

    /**
     * Key check via a lightweight GET on the models list — it needs no JSON body and never fails on a
     * model name, so a valid key returns HTTP 200. Other codes surface the server's real error message.
     */
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
                conn.disconnect(); KeyStatus.Ok
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

    private fun parseError(json: String): String = runCatching {
        JSONObject(json).getJSONObject("error").getString("message")
    }.getOrDefault("")

    /** Returns (httpCode, body) or null on a network-level failure. */
    private fun post(model: String, key: String, body: String): Pair<Int, String>? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            code to text
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            -1 to ""
        }
    }

    private fun parseText(resp: String): String = runCatching {
        JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }.getOrDefault("")

    private fun readIsDrink(text: String): Boolean? {
        val t = text.lowercase()
        return when {
            Regex("\"is_drink\"\\s*:\\s*true").containsMatchIn(t) -> true
            Regex("\"is_drink\"\\s*:\\s*false").containsMatchIn(t) -> false
            t.contains("\"is_drink\"") -> null
            t.contains("yes") && !t.contains("no") -> true
            t.contains("no") && !t.contains("yes") -> false
            else -> null
        }
    }

    private fun readLabel(text: String): String =
        Regex("\"label\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.getOrNull(1)?.ifBlank { "cloud" } ?: "cloud"

    /** Compress to a small JPEG to keep the upload tiny and fast. */
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
