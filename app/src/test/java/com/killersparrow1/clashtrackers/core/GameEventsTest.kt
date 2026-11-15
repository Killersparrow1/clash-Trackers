package com.killersparrow1.clashtrackers.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEventsTest {
    private fun at(iso: String) = Instant.parse(iso).epochSecond
    private fun event(now: String, id: String) = GameEvents.upcoming(at(now)).first { it.id == id }

    @Test fun raidWeekendRunsFridayToMonday() {
        val e = event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND) // a Sunday
        assertTrue(e.active)
        assertEquals(at("2026-11-13T07:00:00Z"), e.startsAt)
        assertEquals(at("2026-11-16T07:00:00Z"), e.endsAt)
        val mid = event("2026-11-18T12:00:00Z", GameEvents.RAID_WEEKEND) // a Wednesday: next Friday
        assertFalse(mid.active)
        assertEquals(at("2026-11-20T07:00:00Z"), mid.startsAt)
        // Friday before 07:00 is still the previous gap
        assertEquals(at("2026-11-20T07:00:00Z"), event("2026-11-20T06:00:00Z", GameEvents.RAID_WEEKEND).startsAt)
    }

    @Test fun clanGamesRunFrom22ndTo28th() {
        val before = event("2026-11-15T00:00:00Z", GameEvents.CLAN_GAMES)
        assertFalse(before.active)
        assertEquals(at("2026-11-22T08:00:00Z"), before.startsAt)
        val during = event("2026-11-24T12:00:00Z", GameEvents.CLAN_GAMES)
        assertTrue(during.active)
        assertEquals(at("2026-11-28T08:00:00Z"), during.endsAt)
        // After the 28th the next one is next month's
        assertEquals(at("2026-12-22T08:00:00Z"), event("2026-11-29T00:00:00Z", GameEvents.CLAN_GAMES).startsAt)
    }

    @Test fun seasonEndsOnTheFirstAndLeagueResetsEvery28Days() {
        assertEquals(at("2026-12-01T08:00:00Z"), event("2026-11-15T00:00:00Z", GameEvents.SEASON_END).endsAt)
        assertEquals(at("2027-01-01T08:00:00Z"), event("2026-12-01T09:00:00Z", GameEvents.SEASON_END).endsAt)
        assertEquals(at("2026-11-30T05:00:00Z"), event("2026-11-15T00:00:00Z", GameEvents.LEAGUE_RESET).endsAt)
        assertEquals(at("2025-12-01T05:00:00Z"), event("2025-11-03T05:00:00Z", GameEvents.LEAGUE_RESET).endsAt)
    }

    @Test fun cwlHasSignupThenWarDays() {
        val signup = event("2026-11-02T00:00:00Z", GameEvents.CWL_SIGNUP)
        assertTrue(signup.active)
        assertEquals(at("2026-11-04T08:00:00Z"), signup.endsAt)
        val wars = event("2026-11-06T00:00:00Z", GameEvents.CWL_WARS)
        assertTrue(wars.active)
        assertEquals(at("2026-11-11T08:00:00Z"), wars.endsAt)
    }

    @Test fun activeEventsComeFirstThenSoonest() {
        val list = GameEvents.upcoming(at("2026-11-15T12:00:00Z"))
        assertEquals(GameEvents.RAID_WEEKEND, list.first().id) // the only one running
        val rest = list.drop(1).map { it.target }
        assertEquals(rest.sorted(), rest)
    }
}
