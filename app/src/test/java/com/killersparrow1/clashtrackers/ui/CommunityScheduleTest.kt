package com.killersparrow1.clashtrackers.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityScheduleTest {
    private val s = CommunitySchedule

    @Test fun picksADayFromTodayToMonthEnd() {
        val today = LocalDate.of(2026, 11, 28)
        val st = s.plan(CommunityState(), today) { it - 1 }
        assertEquals(30, st.targetDay)
        assertEquals(28, s.plan(CommunityState(), today) { 0 }.targetDay)
    }

    @Test fun notDueBeforeChosenDay() {
        val st = s.plan(CommunityState(), LocalDate.of(2026, 11, 1)) { 14 }
        assertFalse(s.due(st, LocalDate.of(2026, 11, 14)))
        assertTrue(s.due(st, LocalDate.of(2026, 11, 15)))
    }

    @Test fun onceAPerMonth() {
        val d = LocalDate.of(2026, 11, 20)
        val st = s.shown(s.plan(CommunityState(), d) { 0 }, d)
        assertFalse(s.due(st, LocalDate.of(2026, 11, 30)))
    }

    @Test fun cooldownIsFourteenDays() {
        val shown = LocalDate.of(2026, 10, 31)
        val st = s.shown(CommunityState(), shown)
        val planned = s.plan(st, LocalDate.of(2026, 11, 1)) { 0 }
        assertEquals(14, planned.targetDay)
        assertFalse(s.due(planned, LocalDate.of(2026, 11, 13)))
        assertTrue(s.due(planned, LocalDate.of(2026, 11, 14)))
    }

    @Test fun noEligibleDayMeansSkipMonth() {
        val st = s.shown(CommunityState(), LocalDate.of(2026, 11, 25))
        val planned = s.plan(st, LocalDate.of(2026, 11, 26)) { 0 }
        assertEquals(0, planned.targetDay)
        assertFalse(s.due(planned, LocalDate.of(2026, 11, 30)))
    }

    @Test fun neverAgainOnceEveryButtonWasUsed() {
        val st = s.plan(CommunityState(), LocalDate.of(2026, 11, 1)) { 0 }
        assertTrue(s.due(st.copy(clicked = CLICK_ALL - 1), LocalDate.of(2026, 11, 5)))
        assertFalse(s.due(st.copy(clicked = CLICK_ALL), LocalDate.of(2026, 11, 5)))
    }

    @Test fun chosenDayIsKeptForTheMonth() {
        val st = s.plan(CommunityState(), LocalDate.of(2026, 11, 2)) { 5 }
        assertEquals(st, s.plan(st, LocalDate.of(2026, 11, 9)) { 0 })
    }
}
