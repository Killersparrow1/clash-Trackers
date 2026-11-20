package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAlertPlannerTest {
    private val hour = 3600L
    private val window = GameEvent("clan_games", point = false, active = false, startsAt = 1_000 * hour, endsAt = 1_144 * hour)
    private val point = GameEvent("season_end", point = true, active = false, startsAt = 2_000 * hour, endsAt = 2_000 * hour)

    @Test fun windowGivesStartAndEndingAlerts() {
        val a = EventAlertPlanner.alerts(listOf(window), setOf("clan_games"), 6 * hour)
        assertEquals(listOf(1_000 * hour, 1_138 * hour), a.map { it.at })
        assertEquals(listOf(EventAlertPlanner.Kind.STARTS, EventAlertPlanner.Kind.ENDING), a.map { it.kind })
    }

    @Test fun pointEventGivesOneComingAlert() {
        val a = EventAlertPlanner.alerts(listOf(point), setOf("season_end"), 24 * hour)
        assertEquals(1, a.size)
        assertEquals(1_976 * hour, a[0].at)
    }

    @Test fun disabledEventsGiveNothing() =
        assertTrue(EventAlertPlanner.alerts(listOf(window, point), emptySet(), hour).isEmpty())

    @Test fun dueIsOnceAndNotStale() {
        val a = EventAlertPlanner.alerts(listOf(window), setOf("clan_games"), 6 * hour)
        assertEquals(1, EventAlertPlanner.due(a, emptySet(), 1_000 * hour + 60).size)
        assertTrue(EventAlertPlanner.due(a, setOf(a[0].key), 1_000 * hour + 60).isEmpty())     // already shown
        assertTrue(EventAlertPlanner.due(a, emptySet(), 1_000 * hour + 3 * hour).isEmpty())   // 3 h late: dropped
    }

    @Test fun nextWakeIsTheNextAlertOrTheReplan() {
        val a = EventAlertPlanner.alerts(listOf(window), setOf("clan_games"), 6 * hour)
        assertEquals(1_000 * hour, EventAlertPlanner.nextWake(a, listOf(window), setOf("clan_games"), emptySet(), 900 * hour))
        // Both alerts shown: wait for the end of the event, then plan the next occurrence.
        val fired = a.map { it.key }.toSet()
        assertEquals(1_144 * hour + 60, EventAlertPlanner.nextWake(a, listOf(window), setOf("clan_games"), fired, 1_139 * hour))
        assertNull(EventAlertPlanner.nextWake(emptyList(), emptyList(), emptySet(), emptySet(), 0))
    }
}
