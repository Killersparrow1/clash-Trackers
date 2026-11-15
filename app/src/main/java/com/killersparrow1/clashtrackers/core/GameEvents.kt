package com.killersparrow1.clashtrackers.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * One recurring game event. [point] events (season end, league reset) have only a moment, [endsAt]; the others have a
 * window from [startsAt] to [endsAt]. Times are epoch seconds.
 */
data class GameEvent(
    val id: String,
    val point: Boolean,
    val active: Boolean,
    val startsAt: Long,
    val endsAt: Long,
    /** A name for one-off events from the downloaded list; null for the built-in ones (named from the app's strings). */
    val name: String? = null,
) {
    /** The moment the countdown runs to: the end of an active window, otherwise the start (or the moment of a point event). */
    val target: Long get() = if (point || active) endsAt else startsAt
}

/**
 * Computed from the game's usual schedule, in UTC, so it works offline. Supercell can change these times, so the
 * screen says they are the usual schedule and not a promise.
 */
object GameEvents {
    /** Everything that sets when a recurring event happens. A downloaded [EventsConfig] can change any of it. */
    data class Rule(
        val day: Int = 1,
        val hour: Int = 0,
        val lengthDays: Int = 1,
        val weekday: Int = 5,
        val anchor: Long = 0L,
        val periodDays: Int = 28,
    )

    const val CLAN_GAMES = "clan_games"
    const val RAID_WEEKEND = "raid_weekend"
    const val CWL_SIGNUP = "cwl_signup"
    const val CWL_WARS = "cwl_wars"
    const val SEASON_END = "season_end"
    const val LEAGUE_RESET = "league_reset"

    private const val DAY = 86_400L
    private val LEAGUE_ANCHOR = LocalDateTime.of(2025, 11, 3, 5, 0).toEpochSecond(ZoneOffset.UTC)

    /** The usual schedule, used until (and unless) a newer one is downloaded. */
    val DEFAULTS: Map<String, Rule> = mapOf(
        CLAN_GAMES to Rule(day = 22, hour = 8, lengthDays = 6),
        RAID_WEEKEND to Rule(weekday = 5, hour = 7, lengthDays = 3),
        CWL_SIGNUP to Rule(day = 1, hour = 8, lengthDays = 3),
        CWL_WARS to Rule(day = 4, hour = 8, lengthDays = 7),
        SEASON_END to Rule(day = 1, hour = 8),
        LEAGUE_RESET to Rule(anchor = LEAGUE_ANCHOR, periodDays = 28),
    )

    private fun epoch(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).toEpochSecond(ZoneOffset.UTC)

    /** The window of a monthly event that has not finished yet: this month's, or next month's. */
    private fun monthly(id: String, now: Long, rule: Rule): GameEvent {
        val today = LocalDate.ofEpochDay(now / DAY)
        for (offset in -1L..2L) {
            val month = today.withDayOfMonth(1).plusMonths(offset)
            val start = epoch(month.withDayOfMonth(rule.day), rule.hour)
            val end = start + rule.lengthDays * DAY
            if (end > now) return GameEvent(id, point = false, active = now >= start, startsAt = start, endsAt = end)
        }
        error("unreachable")
    }

    private fun raidWeekend(now: Long, rule: Rule): GameEvent {
        var date = LocalDate.ofEpochDay(now / DAY)
        val weekday = DayOfWeek.of(rule.weekday)
        while (date.dayOfWeek != weekday) date = date.minusDays(1)
        var start = epoch(date, rule.hour)
        if (start > now) start -= 7 * DAY
        var end = start + rule.lengthDays * DAY
        if (end <= now) { start += 7 * DAY; end += 7 * DAY }
        return GameEvent(RAID_WEEKEND, point = false, active = now >= start, startsAt = start, endsAt = end)
    }

    private fun seasonEnd(now: Long, rule: Rule): GameEvent {
        val today = LocalDate.ofEpochDay(now / DAY)
        for (offset in 0L..2L) {
            val at = epoch(today.withDayOfMonth(1).plusMonths(offset).withDayOfMonth(rule.day), rule.hour)
            if (at > now) return GameEvent(SEASON_END, point = true, active = false, startsAt = at, endsAt = at)
        }
        error("unreachable")
    }

    /** The league reset repeats every [Rule.periodDays] days from [Rule.anchor] (05:00 UTC on 3 November 2025 by default). */
    private fun leagueReset(now: Long, rule: Rule): GameEvent {
        val period = rule.periodDays * DAY
        val next = if (now < rule.anchor) rule.anchor else rule.anchor + ((now - rule.anchor) / period + 1) * period
        return GameEvent(LEAGUE_RESET, point = true, active = false, startsAt = next, endsAt = next)
    }

    /**
     * Every event, those running now first (soonest to end), then the rest by what comes up next. [config] is the
     * downloaded schedule: it can change the usual times and add one-off events (which disappear once they end).
     */
    fun upcoming(now: Long, config: EventsConfig = EventsConfig.EMPTY): List<GameEvent> {
        fun rule(id: String): Rule = config.rule(id, DEFAULTS.getValue(id))
        val extras = config.extra.filter { it.end > now }.map {
            GameEvent(it.id, point = false, active = now >= it.start, startsAt = it.start, endsAt = it.end, name = it.name)
        }
        return (
            listOf(
                monthly(CLAN_GAMES, now, rule(CLAN_GAMES)),
                raidWeekend(now, rule(RAID_WEEKEND)),
                monthly(CWL_SIGNUP, now, rule(CWL_SIGNUP)),
                monthly(CWL_WARS, now, rule(CWL_WARS)),
                seasonEnd(now, rule(SEASON_END)),
                leagueReset(now, rule(LEAGUE_RESET)),
            ) + extras
            ).sortedWith(compareByDescending<GameEvent> { it.active }.thenBy { it.target })
    }
}
