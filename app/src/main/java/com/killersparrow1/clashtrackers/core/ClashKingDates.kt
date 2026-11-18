package com.killersparrow1.clashtrackers.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Event dates from ClashKing's API (`https://api.clashk.ing`, the `/v2/dates` endpoints). Times are epoch seconds.
 *
 * What the API really answers (read from ClashKingAPI's source, `packages/api-contracts/src/dashboard-misc.ts` and
 * `workers/api/src/dashboard-misc-runtime.ts`; public, no key):
 *  - `GET /v2/dates/current` -> `{"season":"2026-11","raid":"2026-11-13","legend":"2026-11-16","clan-games":"2026-11"}`.
 *    `raid` is the day the current raid weekend started (a Friday, the start is 07:00 UTC). Season, legend and clan-games
 *    are only a month or a day, with no time.
 *  - `GET /v2/dates/season-start-end?season=YYYY-MM` -> `{"season_start":"...Z","season_end":"...Z"}`
 *    (ISO 8601 UTC; the season named YYYY-MM runs from the 1st of the month before to the 1st of that month, 05:00 UTC).
 * Nothing for Clan Games times, Clan War Leagues or the league reset, so those stay with [GameEvents]' own schedule.
 */
data class ClashKingDates(
    /** When the dates were last fetched successfully, epoch seconds. */
    val fetchedAt: Long,
    /** Start of the raid weekend that ClashKing named as current when [fetchedAt]. */
    val raidStart: Long? = null,
    /** End of the current season (the next 05:00 UTC month boundary) when [fetchedAt]. */
    val seasonEnd: Long? = null,
) {
    fun toJson(): String = buildString {
        append("{\"fetchedAt\":").append(fetchedAt)
        raidStart?.let { append(",\"raidStart\":").append(it) }
        seasonEnd?.let { append(",\"seasonEnd\":").append(it) }
        append('}')
    }

    companion object {
        private const val DAY = 86_400L

        /** A cached copy this old is no longer trusted (the schedule can change). */
        const val MAX_AGE = 14 * DAY

        /** The saved copy, or null when it is not a usable one. */
        fun fromJson(text: String): ClashKingDates? = runCatching {
            val o = Json.parseToJsonElement(text).jsonObject
            val fetched = o["fetchedAt"]!!.jsonPrimitive.longOrNull ?: return null
            val raid = o["raidStart"]?.jsonPrimitive?.longOrNull
            val season = o["seasonEnd"]?.jsonPrimitive?.longOrNull
            if (fetched <= 0 || (raid == null && season == null)) null else ClashKingDates(fetched, raid, season)
        }.getOrNull()

        /**
         * The start of the current raid weekend from a `/v2/dates/current` answer, at [hour] UTC on the day ClashKing
         * gives, or null when the answer is not what we expect (no `raid`, not a date, not a Friday).
         */
        fun parseRaidStart(text: String, hour: Int = 7): Long? = runCatching {
            val raid = Json.parseToJsonElement(text).jsonObject["raid"]?.jsonPrimitive?.contentOrNull ?: return null
            val day = LocalDate.parse(raid)
            if (day.dayOfWeek != java.time.DayOfWeek.FRIDAY || hour !in 0..23) null
            else LocalDateTime.of(day, java.time.LocalTime.of(hour, 0)).toEpochSecond(ZoneOffset.UTC)
        }.getOrNull()

        /** `season` of a `/v2/dates/current` answer (e.g. `2026-11`), or null. */
        fun parseSeasonId(text: String): String? = runCatching {
            Json.parseToJsonElement(text).jsonObject["season"]?.jsonPrimitive?.contentOrNull?.takeIf { Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(it) }
        }.getOrNull()

        /**
         * The start and end of a season from a `/v2/dates/season-start-end` answer, or null when it is not two ISO times
         * with the end 20 to 40 days after the start.
         */
        fun parseSeasonBounds(text: String): Pair<Long, Long>? = runCatching {
            val o: JsonObject = Json.parseToJsonElement(text).jsonObject
            val start = Instant.parse(o["season_start"]!!.jsonPrimitive.content).epochSecond
            val end = Instant.parse(o["season_end"]!!.jsonPrimitive.content).epochSecond
            if (end - start in 20 * DAY..40 * DAY) start to end else null
        }.getOrNull()

        /** The season ids to ask for: the one named current and the one after (ClashKing names a season by the month it ends). */
        fun seasonCandidates(currentSeason: String?, now: Long): List<String> {
            val base = currentSeason?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
                ?: java.time.YearMonth.from(Instant.ofEpochSecond(now).atZone(ZoneOffset.UTC))
            return listOf(base.toString(), base.plusMonths(1).toString())
        }
    }
}
