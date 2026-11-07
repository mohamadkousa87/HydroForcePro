package com.hydroforce.pro.data

import org.json.JSONArray
import org.json.JSONObject

/** A one-off busy window during which hydration alarms are postponed. */
data class Meeting(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val title: String = ""
) {
    fun contains(t: Long): Boolean = t in startMillis until endMillis
}

/** Serialization helpers so meetings can live in a single DataStore string preference. */
object MeetingCodec {

    fun encode(meetings: List<Meeting>): String {
        val arr = JSONArray()
        meetings.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("start", m.startMillis)
                    .put("end", m.endMillis)
                    .put("title", m.title)
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<Meeting> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Meeting(
                    id = o.optLong("id"),
                    startMillis = o.optLong("start"),
                    endMillis = o.optLong("end"),
                    title = o.optString("title", "")
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** The meeting covering [t], if any. */
fun List<Meeting>.covering(t: Long): Meeting? = firstOrNull { it.contains(t) }
