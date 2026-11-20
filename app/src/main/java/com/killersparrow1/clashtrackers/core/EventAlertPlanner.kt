package com.killersparrow1.clashtrackers.core

/**
 * Works out when to alert about the game's events (Clash tab). Pure, so it can be unit tested.
 *
 * Each event gives: a "starts" alert (windowed events), and a "ends soon" / "coming up" alert [leadSec] before the end
 * (windowed events) or before the moment itself (season end, league reset). Every alert has a [key] that is unique to
 * that occurrence, so one that has already been shown is never shown twice.
 */
object EventAlertPlanner {
    enum class Kind { STARTS, ENDING, COMING }

    data class Alert(val at: Long, val key: String, val eventId: String, val name: String?, val kind: Kind)

    /** Alerts that are older than this when the phone gets to them (it was off, say) are dropped instead of shown late. */
    const val STALE_SEC = 2 * 3600L

    val LEADS = listOf(3600L, 6 * 3600L, 24 * 3600L)

    fun alerts(events: List<GameEvent>, enabled: Set<String>, leadSec: Long): List<Alert> = buildList {
        for (e in events) {
            if (e.id !in enabled) continue
            if (e.point) {
                add(Alert(e.endsAt - leadSec, "${e.id}@${e.endsAt}@c", e.id, e.name, Kind.COMING))
            } else {
                add(Alert(e.startsAt, "${e.id}@${e.startsAt}@s", e.id, e.name, Kind.STARTS))
                if (e.endsAt - leadSec > e.startsAt) add(Alert(e.endsAt - leadSec, "${e.id}@${e.endsAt}@e", e.id, e.name, Kind.ENDING))
            }
        }
    }

    /** Alerts to show right now: their time has come, not too long ago, and not shown before. */
    fun due(all: List<Alert>, fired: Set<String>, now: Long): List<Alert> =
        all.filter { it.at <= now && now - it.at <= STALE_SEC && it.key !in fired }.sortedBy { it.at }

    /**
     * When to wake next: the next alert that is still to come, or just after the next event ends (when the next
     * occurrence appears and the plan has to be made again), whichever is first. Null when there is nothing to wait for.
     */
    fun nextWake(all: List<Alert>, events: List<GameEvent>, enabled: Set<String>, fired: Set<String>, now: Long): Long? {
        val alertAt = all.filter { it.at > now && it.key !in fired }.minOfOrNull { it.at }
        val replan = events.filter { it.id in enabled && it.endsAt > now }.minOfOrNull { it.endsAt + 60 }
        return listOfNotNull(alertAt, replan).minOrNull()
    }
}
