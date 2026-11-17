package com.killersparrow1.clashtrackers.core

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A one-off event from the downloaded list (a special event, a sale), shown until it ends. Times are epoch seconds. */
data class ExtraEvent(val id: String, val name: String, val start: Long, val end: Long)

/**
 * The downloadable event schedule: changes to the usual times plus one-off events. The file lives in the project's
 * repository (`data/events.json`), so a schedule change is a data push and not an app release. Anything invalid in it
 * is ignored rather than trusted.
 */
class EventsConfig(private val overrides: Map<String, JsonObject>, val extra: List<ExtraEvent>) {

    /** True when the downloaded schedule file sets the times of [id] itself; that then wins over any other source. */
    fun hasOverride(id: String): Boolean = id in overrides

    /** [default] with whatever the downloaded file sets for [id]. */
    fun rule(id: String, default: GameEvents.Rule): GameEvents.Rule {
        val o = overrides[id] ?: return default
        fun int(key: String, range: IntRange): Int? = o[key]?.jsonPrimitive?.intOrNull?.takeIf { it in range }
        return default.copy(
            day = int("day", 1..28) ?: default.day,
            hour = int("hour", 0..23) ?: default.hour,
            lengthDays = int("lengthDays", 1..31) ?: default.lengthDays,
            weekday = int("weekday", 1..7) ?: default.weekday,
            periodDays = int("periodDays", 1..365) ?: default.periodDays,
            anchor = o["anchor"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() } ?: default.anchor,
        )
    }

    companion object {
        val EMPTY = EventsConfig(emptyMap(), emptyList())
        private val KNOWN = GameEvents.DEFAULTS.keys

        /** The parsed file, or null when it is not a usable events file. */
        fun parse(text: String): EventsConfig? = runCatching {
            val root = Json.parseToJsonElement(text).jsonObject
            val overrides = root["overrides"]?.jsonObject.orEmpty().filterKeys { it in KNOWN }.mapValues { it.value.jsonObject }
            val extra = root["extra"]?.jsonArray.orEmpty().mapNotNull { e ->
                runCatching {
                    val o = e.jsonObject
                    val name = o["name"]!!.jsonPrimitive.content.trim().take(60)
                    val start = Instant.parse(o["start"]!!.jsonPrimitive.content).epochSecond
                    val end = Instant.parse(o["end"]!!.jsonPrimitive.content).epochSecond
                    val id = (o["id"]?.jsonPrimitive?.contentOrNull ?: name).take(40)
                    if (name.isEmpty() || end <= start || id in KNOWN) null else ExtraEvent(id, name, start, end)
                }.getOrNull()
            }.take(20)
            EventsConfig(overrides, extra)
        }.getOrNull()
    }
}
