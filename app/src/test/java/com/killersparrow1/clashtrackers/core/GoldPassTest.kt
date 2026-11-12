package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldPassTest {
    private val day = 86_400L

    @Test fun twentyPercentOffTenDaysIsEightDays() {
        assertEquals(8 * day, GoldPassReduction(builderPercent = 20).reduce(10 * day, JobCategory.BUILDER))
    }

    @Test fun builderAndResearchPercentsAreSeparate() {
        val gp = GoldPassReduction(builderPercent = 10, researchPercent = 15)
        assertEquals(90L, gp.reduce(100, JobCategory.BUILDER))
        assertEquals(85L, gp.reduce(100, JobCategory.LAB))
    }

    @Test fun petsAndBuilderBaseAreNotReduced() {
        val gp = GoldPassReduction(20, 20)
        for (c in listOf(JobCategory.PET, JobCategory.BB_BUILDER, JobCategory.STAR_LAB)) assertEquals(1000L, gp.reduce(1000, c))
    }

    @Test fun noGoldPassChangesNothing() {
        assertEquals(12345L, GoldPassReduction.NONE.reduce(12345, JobCategory.BUILDER))
    }

    @Test fun monthKeyIsUtcCalendarMonth() {
        assertEquals("2026-11", GoldPassLogic.monthKey(1_794_000_000L)) // 2026-11-xx
        assertEquals("2026-12", GoldPassLogic.monthKey(java.time.LocalDate.of(2026, 12, 1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()))
        assertEquals("2026-11", GoldPassLogic.monthKey(java.time.LocalDate.of(2026, 12, 1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() - 1))
    }

    @Test fun askedOnceAMonth() {
        assertTrue(GoldPassLogic.needsAsk(null, "2026-11", true))
        assertTrue(GoldPassLogic.needsAsk("2026-10", "2026-11", true))
        assertFalse(GoldPassLogic.needsAsk("2026-11", "2026-11", true))
        assertFalse(GoldPassLogic.needsAsk("2026-10", "2026-11", false))
    }
}
