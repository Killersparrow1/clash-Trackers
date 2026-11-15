package com.killersparrow1.clashtrackers.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * One recurring game event. [point] events (season end, league reset) have only a moment, [endsAt]; the others have a
 * window from [startsAt] to [endsAt]. Times are epoch seconds.
 */
data class GameEvent(val id: String, val point: Boolean, val active: Boolean, val startsAt: Long, val endsAt: Long) {
    /** The moment the countdown runs to: the end of an active window, otherwise the start (or the moment of a point event). */
    val target: Long get() = if (point || active) endsAt else startsAt
}

/**
 * Computed from the game's usual schedule, in UTC, so it works offline. Supercell can change these times, so the
 * screen says they are the usual schedule and not a promise.
 */
object GameEvents {
    const val CLAN_GAMES = "clan_games"
    const val RAID_WEEKEND = "raid_weekend"
    const val CWL_SIGNUP = "cwl_signup"
    const val CWL_WARS = "cwl_wars"
    const val SEASON_END = "season_end"
    const val LEAGUE_RESET = "league_reset"

    private const val DAY = 86_400L
    private val LEAGUE_ANCHOR = LocalDateTime.of(2025, 11, 3, 5, 0).toEpochSecond(ZoneOffset.UTC)

    private fun epoch(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).toEpochSecond(ZoneOffset.UTC)

    /** The window of a monthly event that has not finished yet: this month's, or next month's. */
    private fun monthly(id: String, now: Long, day: Int, hour: Int, lengthDays: Int): GameEvent {
        val today = LocalDate.ofEpochDay(now / DAY)
        for (offset in -1L..2L) {
            val month = today.withDayOfMonth(1).plusMonths(offset)
            val start = epoch(month.withDayOfMonth(day), hour)
            val end = start + lengthDays * DAY
            if (end > now) return GameEvent(id, point = false, active = now >= start, startsAt = start, endsAt = end)
        }
        error("unreachable")
    }

    private fun raidWeekend(now: Long): GameEvent {
        var date = LocalDate.ofEpochDay(now / DAY)
        while (date.dayOfWeek != DayOfWeek.FRIDAY) date = date.minusDays(1)
        var start = epoch(date, 7)
        if (start > now) start -= 7 * DAY
        var end = start + 3 * DAY
        if (end <= now) { start += 7 * DAY; end += 7 * DAY }
        return GameEvent(RAID_WEEKEND, point = false, active = now >= start, startsAt = start, endsAt = end)
    }

    private fun seasonEnd(now: Long): GameEvent {
        val today = LocalDate.ofEpochDay(now / DAY)
        for (offset in 0L..2L) {
            val first = epoch(today.withDayOfMonth(1).plusMonths(offset), 8)
            if (first > now) return GameEvent(SEASON_END, point = true, active = false, startsAt = first, endsAt = first)
        }
        error("unreachable")
    }

    /** The league reset runs on a 28-day cycle (05:00 UTC) since 3 November 2025. */
    private fun leagueReset(now: Long): GameEvent {
        val period = 28 * DAY
        val next = if (now < LEAGUE_ANCHOR) LEAGUE_ANCHOR else LEAGUE_ANCHOR + ((now - LEAGUE_ANCHOR) / period + 1) * period
        return GameEvent(LEAGUE_RESET, point = true, active = false, startsAt = next, endsAt = next)
    }

    /** Every event, those running now first (soonest to end), then the rest by what comes up next. */
    fun upcoming(now: Long): List<GameEvent> =
        listOf(
            monthly(CLAN_GAMES, now, day = 22, hour = 8, lengthDays = 6),
            raidWeekend(now),
            monthly(CWL_SIGNUP, now, day = 1, hour = 8, lengthDays = 3),
            monthly(CWL_WARS, now, day = 4, hour = 8, lengthDays = 7),
            seasonEnd(now),
            leagueReset(now),
        ).sortedWith(compareByDescending<GameEvent> { it.active }.thenBy { it.target })
}
