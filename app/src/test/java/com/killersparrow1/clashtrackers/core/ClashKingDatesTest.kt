package com.killersparrow1.clashtrackers.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The response shapes below are the ones ClashKing's API source defines (ClashKingAPI,
 * `packages/api-contracts/src/dashboard-misc.ts`): `/v2/dates/current` and `/v2/dates/season-start-end`.
 */
class ClashKingDatesTest {
    private fun at(iso: String) = Instant.parse(iso).epochSecond
    private val current = """{"season":"2026-11","raid":"2026-11-13","legend":"2026-11-16","clan-games":"2026-11"}"""
    private val bounds = """{"season_start":"2026-11-01T05:00:00Z","season_end":"2026-12-01T05:00:00Z"}"""

    @Test fun readsTheRaidStartFromTheCurrentAnswer() {
        assertEquals(at("2026-11-13T07:00:00Z"), ClashKingDates.parseRaidStart(current))
        assertEquals(at("2026-11-13T09:00:00Z"), ClashKingDates.parseRaidStart(current, hour = 9))
        assertEquals("2026-11", ClashKingDates.parseSeasonId(current))
    }

    @Test fun rejectsUnusableCurrentAnswers() {
        assertNull(ClashKingDates.parseRaidStart("""{"raid":"2026-11-14"}""")) // a Saturday
        assertNull(ClashKingDates.parseRaidStart("""{"raid":"soon"}"""))
        assertNull(ClashKingDates.parseRaidStart("""{"season":"2026-11"}"""))
        assertNull(ClashKingDates.parseRaidStart("""{"raid":null}"""))
        assertNull(ClashKingDates.parseRaidStart("not json"))
        assertNull(ClashKingDates.parseRaidStart(""))
        assertNull(ClashKingDates.parseRaidStart(current, hour = 30))
        assertNull(ClashKingDates.parseSeasonId("""{"season":"November"}"""))
        assertNull(ClashKingDates.parseSeasonId("[]"))
    }

    @Test fun readsSeasonBounds() {
        assertEquals(at("2026-11-01T05:00:00Z") to at("2026-12-01T05:00:00Z"), ClashKingDates.parseSeasonBounds(bounds))
        // ClashKing writes the gold pass variant at midnight, with or without fractional seconds
        assertNotNull(ClashKingDates.parseSeasonBounds("""{"season_start":"2026-11-01T00:00:00.000Z","season_end":"2026-12-01T00:00:00Z"}"""))
    }

    @Test fun rejectsUnusableSeasonBounds() {
        assertNull(ClashKingDates.parseSeasonBounds("""{"season_start":"2026-12-01T05:00:00Z","season_end":"2026-11-01T05:00:00Z"}"""))
        assertNull(ClashKingDates.parseSeasonBounds("""{"season_start":"2026-11-01T05:00:00Z","season_end":"2026-11-04T05:00:00Z"}"""))
        assertNull(ClashKingDates.parseSeasonBounds("""{"season_start":"yesterday","season_end":"tomorrow"}"""))
        assertNull(ClashKingDates.parseSeasonBounds("""{"season_start":"2026-11-01T05:00:00Z"}"""))
        assertNull(ClashKingDates.parseSeasonBounds("{}"))
        assertNull(ClashKingDates.parseSeasonBounds("<html>502</html>"))
    }

    @Test fun asksForTheNamedSeasonAndTheNextOne() {
        assertEquals(listOf("2026-11", "2026-12"), ClashKingDates.seasonCandidates("2026-11", at("2026-11-15T00:00:00Z")))
        assertEquals(listOf("2026-12", "2027-01"), ClashKingDates.seasonCandidates("2026-12", at("2026-12-15T00:00:00Z")))
        // no usable id: from the clock
        assertEquals(listOf("2026-11", "2026-12"), ClashKingDates.seasonCandidates(null, at("2026-11-15T00:00:00Z")))
        assertEquals(listOf("2026-11", "2026-12"), ClashKingDates.seasonCandidates("garbage", at("2026-11-15T00:00:00Z")))
    }

    @Test fun savedCopySurvivesARoundTripAndRejectsDamage() {
        val d = ClashKingDates(at("2026-11-15T00:00:00Z"), at("2026-11-13T07:00:00Z"), at("2026-12-01T05:00:00Z"))
        assertEquals(d, ClashKingDates.fromJson(d.toJson()))
        val onlyRaid = ClashKingDates(100, raidStart = 5)
        assertEquals(onlyRaid, ClashKingDates.fromJson(onlyRaid.toJson()))
        assertNull(ClashKingDates.fromJson("{"))
        assertNull(ClashKingDates.fromJson(""))
        assertNull(ClashKingDates.fromJson("""{"fetchedAt":100}""")) // nothing in it
        assertNull(ClashKingDates.fromJson("""{"fetchedAt":"x","raidStart":5}"""))
        assertNull(ClashKingDates.fromJson("""{"fetchedAt":0,"raidStart":5}"""))
    }

    // --- how the dates are used by the schedule

    private fun event(now: String, id: String, dates: ClashKingDates?, config: EventsConfig = EventsConfig.EMPTY) =
        GameEvents.upcoming(at(now), config, dates).first { it.id == id }

    private val fetched = at("2026-11-15T00:00:00Z")

    @Test fun raidWeekendUsesClashKingsStartAndSaysSo() {
        val dates = ClashKingDates(fetched, raidStart = at("2026-11-13T09:00:00Z")) // 09:00, unlike the usual 07:00
        val e = event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND, dates)
        assertTrue(e.fromApi)
        assertTrue(e.active)
        assertEquals(at("2026-11-13T09:00:00Z"), e.startsAt)
        assertEquals(at("2026-11-16T09:00:00Z"), e.endsAt)
        assertFalse(event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND, null).fromApi)
    }

    @Test fun raidWeekendMovesOnByWholeWeeksWithoutAskingAgain() {
        val dates = ClashKingDates(fetched, raidStart = at("2026-11-13T07:00:00Z"))
        val e = event("2026-11-25T12:00:00Z", GameEvents.RAID_WEEKEND, dates) // two weeks after the answer
        assertTrue(e.fromApi)
        assertFalse(e.active)
        assertEquals(at("2026-11-27T07:00:00Z"), e.startsAt)
        assertEquals(at("2026-11-30T07:00:00Z"), e.endsAt)
    }

    @Test fun staleOrImplausibleDatesFallBackToTheUsualSchedule() {
        val old = ClashKingDates(at("2026-10-01T00:00:00Z"), raidStart = at("2026-09-25T07:00:00Z"), seasonEnd = at("2026-12-01T05:00:00Z"))
        assertFalse(event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND, old).fromApi) // 6 weeks old
        assertFalse(event("2026-11-15T12:00:00Z", GameEvents.SEASON_END, old).fromApi)
        val future = ClashKingDates(at("2026-11-20T00:00:00Z"), raidStart = at("2026-11-13T07:00:00Z")) // clock set back
        assertFalse(event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND, future).fromApi)
        val faraway = ClashKingDates(fetched, raidStart = at("2026-12-25T07:00:00Z")) // not the current weekend
        assertFalse(event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND, faraway).fromApi)
    }

    @Test fun seasonEndComesFromClashKingWhileItIsInTheFuture() {
        val dates = ClashKingDates(fetched, seasonEnd = at("2026-12-01T05:00:00Z"))
        val e = event("2026-11-15T12:00:00Z", GameEvents.SEASON_END, dates)
        assertTrue(e.fromApi)
        assertTrue(e.point)
        assertEquals(at("2026-12-01T05:00:00Z"), e.endsAt)
        // once it has passed, the usual schedule (08:00 UTC, three hours after ClashKing's 05:00) takes over
        assertEquals(at("2026-12-01T08:00:00Z"), event("2026-12-01T06:00:00Z", GameEvents.SEASON_END, dates).endsAt)
        val after = event("2026-12-01T09:00:00Z", GameEvents.SEASON_END, dates)
        assertFalse(after.fromApi)
        assertEquals(at("2027-01-01T08:00:00Z"), after.endsAt)
    }

    @Test fun theScheduleFilesOwnTimesWinOverClashKing() {
        val config = EventsConfig.parse("""{"overrides":{"raid_weekend":{"hour":8},"season_end":{"hour":9}}}""")!!
        val dates = ClashKingDates(fetched, raidStart = at("2026-11-13T07:00:00Z"), seasonEnd = at("2026-12-01T05:00:00Z"))
        val raid = event("2026-11-15T12:00:00Z", GameEvents.RAID_WEEKEND, dates, config)
        assertFalse(raid.fromApi)
        assertEquals(at("2026-11-13T08:00:00Z"), raid.startsAt)
        val season = event("2026-11-15T12:00:00Z", GameEvents.SEASON_END, dates, config)
        assertFalse(season.fromApi)
        assertEquals(at("2026-12-01T09:00:00Z"), season.endsAt)
    }

    @Test fun otherEventsAreNeverTakenFromTheApi() {
        val dates = ClashKingDates(fetched, raidStart = at("2026-11-13T07:00:00Z"), seasonEnd = at("2026-12-01T05:00:00Z"))
        val events = GameEvents.upcoming(at("2026-11-15T12:00:00Z"), EventsConfig.EMPTY, dates)
        assertEquals(6, events.size)
        for (id in listOf(GameEvents.CLAN_GAMES, GameEvents.CWL_SIGNUP, GameEvents.CWL_WARS, GameEvents.LEAGUE_RESET)) {
            assertFalse(id, events.first { it.id == id }.fromApi)
        }
        // and the schedule without any dates is exactly what it was
        assertEquals(GameEvents.upcoming(at("2026-11-15T12:00:00Z")), GameEvents.upcoming(at("2026-11-15T12:00:00Z"), EventsConfig.EMPTY, null))
    }
}
