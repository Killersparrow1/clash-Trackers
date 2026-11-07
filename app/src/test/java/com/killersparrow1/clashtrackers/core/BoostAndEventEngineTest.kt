package com.killersparrow1.clashtrackers.core

import com.killersparrow1.clashtrackers.ui.formatTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class BoostAndEventEngineTest {

    private val t0 = 1_000_000L
    private val engine = TimerEngine()

    private fun sampleJob(
        category: JobCategory = JobCategory.BUILDER,
        source: JobSource = JobSource.BUILDING,
        dataId: Int = 1000001,
        currentLevel: Int = 1,
        remaining: Long = 36000L, // 10 hours
        occurrence: Int = 0,
    ) = Job(
        category = category,
        source = source,
        dataId = dataId,
        currentLevel = currentLevel,
        remainingAtT0 = remaining,
        helperLinked = false,
        occurrence = occurrence,
    )

    private fun sampleExport(jobs: List<Job>) = VillageExport(
        tag = "#TEST",
        timestamp = t0,
        townHallLevel = 16,
        builderHutCount = 5,
        hasBobsHut = true,
        helpers = emptyList(),
        jobs = jobs,
    )

    // ==========================================
    // NORMAL TIMER
    // ==========================================

    @Test fun test1_noActiveModifier() {
        val job = sampleJob(remaining = 7200L) // 2 hours
        val exp = sampleExport(listOf(job))
        val finish = engine.finishTime(job, exp, HelperLinks())
        assertEquals(t0 + 7200L, finish)
    }

    @Test fun test2_upgradeCompletesBeforeScheduledBoost() {
        val job = sampleJob(remaining = 1800L) // 30 min
        val exp = sampleExport(listOf(job))
        // Boost starts 1 hour after t0
        val scheduledBoost = BoostModifier(
            id = "bpot_sched",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            startTimestamp = t0 + 3600L,
            endTimestamp = t0 + 7200L,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(scheduledBoost))
        assertEquals(t0 + 1800L, finish)
    }

    @Test fun test3_upgradeCompletionDuringAnEvent() {
        // Upgrade needs 10,000s normally.
        // Event gives 50% time reduction (2.0x speed, extraRate = 1.0) from t0 to t0 + 20,000s.
        val job = sampleJob(remaining = 10000L)
        val exp = sampleExport(listOf(job))
        val event = EventModifier(
            id = "hj",
            name = "Hammer Jam",
            type = EventType.HAMMER_JAM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 20000L,
            enabled = true,
            eligibleCategories = setOf(JobCategory.BUILDER),
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), events = listOf(event))
        // At 2x speed, 10,000s remaining finishes in 5,000 real seconds
        assertEquals(t0 + 5000L, finish)
    }

    // ==========================================
    // CLOCK TOWER
    // ==========================================

    @Test fun test4_clockTowerBoostActiveImmediately() {
        // Builder Base building with 10,000s remaining.
        // Clock Tower lvl 9 gives 30 min (1800s) of 10x speed.
        val job = sampleJob(category = JobCategory.BB_BUILDER, source = JobSource.BB_BUILDING, remaining = 20000L)
        val exp = sampleExport(listOf(job))
        val ctBoost = BoostModifier(
            id = "ct",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1800L,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(ctBoost))
        // During 1800s at 10x speed: consumes 18,000s of work!
        // Remaining work after 1800s: 20,000 - 18,000 = 2,000s.
        // That 2,000s runs at 1x speed: finishes at t0 + 1800 + 2000 = t0 + 3800s.
        assertEquals(t0 + 3800L, finish)
    }

    @Test fun test5_clockTowerBoostActivatedMidway() {
        val job = sampleJob(category = JobCategory.BB_BUILDER, source = JobSource.BB_BUILDING, remaining = 20000L)
        val exp = sampleExport(listOf(job))
        // Runs at 1x for 1000s (consuming 1000s work, 19,000s remaining)
        // Then Clock Tower activates from t0 + 1000 to t0 + 2800 (1800s at 10x = 18,000s work)
        // Work left = 19,000 - 18,000 = 1,000s at 1x -> 1000s.
        // Total finish: t0 + 1000 + 1800 + 1000 = t0 + 3800s.
        val ctBoost = BoostModifier(
            id = "ct_mid",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0 + 1000L,
            endTimestamp = t0 + 2800L,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(ctBoost))
        assertEquals(t0 + 3800L, finish)
    }

    @Test fun test6_clockTowerBoostExpiration() {
        val job = sampleJob(category = JobCategory.BB_BUILDER, source = JobSource.BB_BUILDING, remaining = 30000L)
        val exp = sampleExport(listOf(job))
        val ctBoost = BoostModifier(
            id = "ct_exp",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1800L,
        )
        // Check remaining right at expiration: 30,000 - 18,000 = 12,000s left at t0 + 1800.
        val remAtExp = engine.remainingAt(job, exp, HelperLinks(), modifiers = listOf(ctBoost), t = t0 + 1800L)
        assertEquals(12000L, remAtExp)
    }

    @Test fun test7_clockTowerPotion() {
        // Clock Tower Potion: verified 30 min (1800s) at 10x.
        val job = sampleJob(category = JobCategory.BB_BUILDER, source = JobSource.BB_BUILDING, remaining = 18000L)
        val exp = sampleExport(listOf(job))
        val ctPotion = BoostModifier(
            id = "ct_pot",
            type = BoostModifierType.CLOCK_TOWER_POTION,
            name = "Clock Tower Potion",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + ClockTowerData.POTION_DURATION_SEC,
        )
        // 18,000s work at 10x speed finishes exactly in 1800 real seconds!
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(ctPotion))
        assertEquals(t0 + 1800L, finish)
    }

    @Test fun test8_multipleBuilderBaseTimers() {
        val b1 = sampleJob(category = JobCategory.BB_BUILDER, source = JobSource.BB_BUILDING, remaining = 18000L, occurrence = 0)
        val b2 = sampleJob(category = JobCategory.BB_BUILDER, source = JobSource.BB_BUILDING, remaining = 36000L, occurrence = 1)
        val exp = sampleExport(listOf(b1, b2))
        val ct = BoostModifier(
            id = "ct",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1800L,
        )
        val finish1 = engine.finishTime(b1, exp, HelperLinks(), modifiers = listOf(ct))
        val finish2 = engine.finishTime(b2, exp, HelperLinks(), modifiers = listOf(ct))
        assertEquals(t0 + 1800L, finish1)
        assertEquals(t0 + 1800L + (36000L - 18000L), finish2)
    }

    @Test fun test9_researchEligibility_StarLab() {
        // Star Laboratory upgrade (category = STAR_LAB) must receive Clock Tower boost.
        val starLab = sampleJob(category = JobCategory.STAR_LAB, source = JobSource.BB_UNIT, remaining = 18000L)
        val exp = sampleExport(listOf(starLab))
        val ct = BoostModifier(
            id = "ct",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1800L,
        )
        val finish = engine.finishTime(starLab, exp, HelperLinks(), modifiers = listOf(ct))
        assertEquals(t0 + 1800L, finish)
    }

    @Test fun test10_wrongVillageEligibility_ClockTowerDoesNotAffectHomeVillage() {
        val homeBuilder = sampleJob(category = JobCategory.BUILDER, source = JobSource.BUILDING, remaining = 18000L)
        val exp = sampleExport(listOf(homeBuilder))
        val ct = BoostModifier(
            id = "ct",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1800L,
        )
        val finish = engine.finishTime(homeBuilder, exp, HelperLinks(), modifiers = listOf(ct))
        // Must progress at 1x speed, completely unaffected.
        assertEquals(t0 + 18000L, finish)
    }

    // ==========================================
    // HOME VILLAGE
    // ==========================================

    @Test fun test11_builderPotion_HomeVillage() {
        // Builder Potion gives 1 hour of 10x speed.
        val job = sampleJob(category = JobCategory.BUILDER, remaining = 36000L)
        val exp = sampleExport(listOf(job))
        val bpot = BoostModifier(
            id = "bpot",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            village = Village.HOME,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 3600L,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(bpot))
        // 1 hr at 10x = 36,000s work consumed! Exactly finishes at t0 + 3600.
        assertEquals(t0 + 3600L, finish)
    }

    @Test fun test12_builderBoost_GoldPassEligibility() {
        val homeJob = sampleJob(category = JobCategory.BUILDER, remaining = 36000L)
        val bbJob = sampleJob(category = JobCategory.BB_BUILDER, remaining = 36000L)
        val exp = sampleExport(listOf(homeJob, bbJob))
        val boostPerk = BoostModifier(
            id = "gp_perk",
            type = BoostModifierType.BUILDER_BOOST_PERK,
            name = "Gold Pass 20%",
            village = Village.HOME,
            multiplier = 1.25, // 20% time reduction = 1 / 0.8 = 1.25x speed
            startTimestamp = t0,
            endTimestamp = t0 + 100000L,
        )
        val finishHome = engine.finishTime(homeJob, exp, HelperLinks(), modifiers = listOf(boostPerk))
        val finishBB = engine.finishTime(bbJob, exp, HelperLinks(), modifiers = listOf(boostPerk))
        assertEquals(t0 + (36000.0 / 1.25).toLong(), finishHome)
        assertEquals(t0 + 36000L, finishBB) // Builder Base unaffected
    }

    @Test fun test13_hammerJam_HomeVillageOnly() {
        val homeJob = sampleJob(category = JobCategory.BUILDER, remaining = 20000L)
        val bbJob = sampleJob(category = JobCategory.BB_BUILDER, remaining = 20000L)
        val exp = sampleExport(listOf(homeJob, bbJob))
        val hammerJam = EventModifier(
            id = "hj",
            name = "Hammer Jam",
            type = EventType.HAMMER_JAM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 100000L,
            enabled = true,
            eligibleCategories = setOf(JobCategory.BUILDER),
        )
        val finishHome = engine.finishTime(homeJob, exp, HelperLinks(), events = listOf(hammerJam))
        val finishBB = engine.finishTime(bbJob, exp, HelperLinks(), events = listOf(hammerJam))
        assertEquals(t0 + 10000L, finishHome) // 50% time reduction = half duration
        assertEquals(t0 + 20000L, finishBB)   // Builder Base not affected
    }

    @Test fun test14_eventExpiration() {
        val job = sampleJob(remaining = 20000L)
        val exp = sampleExport(listOf(job))
        // Event runs only for 5,000s, then expires.
        val shortEvent = EventModifier(
            id = "hj_short",
            name = "Hammer Jam",
            type = EventType.HAMMER_JAM,
            village = Village.HOME,
            timeReductionPercent = 50.0, // 2x speed
            startTimestamp = t0,
            endTimestamp = t0 + 5000L,
            enabled = true,
            eligibleCategories = setOf(JobCategory.BUILDER),
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), events = listOf(shortEvent))
        // During 5000s event at 2x: consumes 10,000s of work.
        // Remaining work = 10,000s, runs at 1x for 10,000s.
        // Total finish: t0 + 5000 + 10000 = t0 + 15,000s.
        assertEquals(t0 + 15000L, finish)
    }

    @Test fun test15_builderPotionInteractionWithOtherModifiers() {
        val job = sampleJob(remaining = 40000L)
        val exp = sampleExport(listOf(job))
        // Builder Potion (10x, extra = 9.0) for 1 hour.
        val bpot = BoostModifier(
            id = "bpot",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            village = Village.HOME,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 3600L,
        )
        // Plus an Event running concurrently (50% reduction = 2x speed, extra = 1.0) for 2 hours.
        val event = EventModifier(
            id = "event",
            name = "Event",
            type = EventType.CUSTOM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 7200L,
            enabled = true,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(bpot), events = listOf(event))
        // Hour 1: rate = 1.0 (base) + 9.0 (potion) + 1.0 (event) = 11.0x speed.
        // In 3600s: consumes 3600 * 11 = 39,600s work!
        // Work remaining after 1 hr = 40,000 - 39,600 = 400s.
        // Hour 2: rate = 1.0 + 1.0 (event) = 2.0x speed.
        // 400s at 2x speed takes 200 real seconds.
        // Finish: t0 + 3600 + 200 = t0 + 3800s.
        assertEquals(t0 + 3800L, finish)
    }

    // ==========================================
    // SPECIAL EVENTS
    // ==========================================

    @Test fun test16_builderBaseBonanza() {
        val bbJob = sampleJob(category = JobCategory.BB_BUILDER, remaining = 20000L)
        val homeJob = sampleJob(category = JobCategory.BUILDER, remaining = 20000L)
        val exp = sampleExport(listOf(bbJob, homeJob))
        val bonanza = EventModifier(
            id = "bb_bonanza",
            name = "Builder Base Bonanza",
            type = EventType.BUILDER_BASE_BONANZA,
            village = Village.BUILDER_BASE,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 100000L,
            enabled = true,
            eligibleCategories = setOf(JobCategory.BB_BUILDER, JobCategory.STAR_LAB),
        )
        val finishBB = engine.finishTime(bbJob, exp, HelperLinks(), events = listOf(bonanza))
        val finishHome = engine.finishTime(homeJob, exp, HelperLinks(), events = listOf(bonanza))
        assertEquals(t0 + 10000L, finishBB) // 50% time reduction
        assertEquals(t0 + 20000L, finishHome) // Home Village untouched
    }

    @Test fun test17_eventStartingDuringAnUpgrade() {
        val job = sampleJob(remaining = 20000L)
        val exp = sampleExport(listOf(job))
        // Event starts 5000s after t0, runs for 10000s at 50% reduction (2x speed)
        val event = EventModifier(
            id = "mid_event",
            name = "Late Event",
            type = EventType.CUSTOM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0 + 5000L,
            endTimestamp = t0 + 15000L,
            enabled = true,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), events = listOf(event))
        // 0 to 5000s: 1x speed consumes 5000s work (15000s left)
        // 5000s to finish: 15000s work at 2x speed takes 7500s.
        // Finish: t0 + 5000 + 7500 = t0 + 12500s.
        assertEquals(t0 + 12500L, finish)
    }

    @Test fun test18_eventEndingDuringAnUpgrade() {
        val job = sampleJob(remaining = 20000L)
        val exp = sampleExport(listOf(job))
        // Event starts at t0, ends at t0 + 4000s
        val event = EventModifier(
            id = "early_event",
            name = "Early Event",
            type = EventType.CUSTOM,
            village = Village.HOME,
            timeReductionPercent = 50.0, // 2x speed
            startTimestamp = t0,
            endTimestamp = t0 + 4000L,
            enabled = true,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), events = listOf(event))
        // 4000s at 2x = 8000s work consumed.
        // 12000s remaining at 1x = 12000s.
        // Finish: t0 + 4000 + 12000 = t0 + 16000s.
        assertEquals(t0 + 16000L, finish)
    }

    @Test fun test19_multipleEventsWithIncompatibleEligibility() {
        val homeJob = sampleJob(category = JobCategory.BUILDER, remaining = 10000L)
        val bbJob = sampleJob(category = JobCategory.BB_BUILDER, remaining = 10000L)
        val exp = sampleExport(listOf(homeJob, bbJob))
        val hj = EventModifier(
            id = "hj",
            name = "Hammer Jam",
            type = EventType.HAMMER_JAM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 20000L,
            enabled = true,
        )
        val bonanza = EventModifier(
            id = "bonanza",
            name = "BB Bonanza",
            type = EventType.BUILDER_BASE_BONANZA,
            village = Village.BUILDER_BASE,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 20000L,
            enabled = true,
        )
        val finishHome = engine.finishTime(homeJob, exp, HelperLinks(), events = listOf(hj, bonanza))
        val finishBB = engine.finishTime(bbJob, exp, HelperLinks(), events = listOf(hj, bonanza))
        // Each event only speeds up its own eligible village!
        assertEquals(t0 + 5000L, finishHome)
        assertEquals(t0 + 5000L, finishBB)
    }

    @Test fun test20_invalidEventDatesIgnored() {
        val job = sampleJob(remaining = 10000L)
        val exp = sampleExport(listOf(job))
        // Invalid: start > end
        val invalidEvent = EventModifier(
            id = "inv",
            name = "Invalid Dates",
            type = EventType.CUSTOM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0 + 5000L,
            endTimestamp = t0 + 2000L,
            enabled = true,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), events = listOf(invalidEvent))
        // Ignored; finishes in standard 10,000s
        assertEquals(t0 + 10000L, finish)
    }

    // ==========================================
    // PERSISTENCE
    // ==========================================

    @Test fun test21_appRestartDuringActiveBoost() {
        val boost = BoostModifier(
            id = "b1",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            village = Village.HOME,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 3600L,
        )
        val state = AppState(activeBoosts = ActiveBoostState(modifiers = listOf(boost)))
        val encoded = state.encode()
        val restored = AppState.decode(encoded)
        assertEquals(1, restored.activeBoosts.modifiers.size)
        assertEquals("b1", restored.activeBoosts.modifiers.first().id)
        assertEquals(10.0, restored.activeBoosts.modifiers.first().multiplier, 0.001)
    }

    @Test fun test22_appRestartDuringEvent() {
        val event = EventModifier(
            id = "e1",
            name = "Hammer Jam",
            type = EventType.HAMMER_JAM,
            village = Village.HOME,
            timeReductionPercent = 50.0,
            startTimestamp = t0,
            endTimestamp = t0 + 86400L,
            enabled = true,
        )
        val state = AppState(eventSettings = EventSettings(events = listOf(event)))
        val restored = AppState.decode(state.encode())
        assertEquals(1, restored.eventSettings.events.size)
        assertTrue(restored.eventSettings.events.first().enabled)
        assertEquals(50.0, restored.eventSettings.events.first().timeReductionPercent, 0.001)
    }

    @Test fun test23_manualActivationTimeCorrection() {
        val boostState = ActiveBoostState()
        val withBoost = boostState.addClockTowerFree(tapTime = t0, level = 9)
        assertEquals(1, withBoost.modifiers.size)
        val id = withBoost.modifiers.first().id
        // User cancels/undoes it at t0 + 600s
        val cancelled = withBoost.cancelActive(id, now = t0 + 600L)
        assertEquals(t0 + 600L, cancelled.modifiers.first().endTimestamp)
    }

    @Test fun test24_deviceTimezoneChange() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val formattedUtc = formatTimestamp(t0)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val formattedTokyo = formatTimestamp(t0)
            // Formatting produces localized time strings without throwing
            assertNotNull(formattedUtc)
            assertNotNull(formattedTokyo)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    // ==========================================
    // BOUNDARIES
    // ==========================================

    @Test fun test25_exactActivationTimestamp() {
        val job = sampleJob(remaining = 10000L)
        val exp = sampleExport(listOf(job))
        val bpot = BoostModifier(
            id = "bpot",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            village = Village.HOME,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1000L,
        )
        // At exact start timestamp t0, remaining is 10,000s
        val rem = engine.remainingAt(job, exp, HelperLinks(), modifiers = listOf(bpot), t = t0)
        assertEquals(10000L, rem)
    }

    @Test fun test26_exactExpirationTimestamp() {
        val job = sampleJob(remaining = 20000L)
        val exp = sampleExport(listOf(job))
        val bpot = BoostModifier(
            id = "bpot",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            village = Village.HOME,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1000L,
        )
        // 1000s at 10x = 10,000s work. At exact expiration t0 + 1000, remaining is exactly 10,000s.
        val rem = engine.remainingAt(job, exp, HelperLinks(), modifiers = listOf(bpot), t = t0 + 1000L)
        assertEquals(10000L, rem)
    }

    @Test fun test27_upgradeCompletingAtBoostExpiration() {
        // Upgrade needs exactly 18,000s work.
        // Boost runs for 1800s at 10x speed = exactly 18,000s work.
        val job = sampleJob(remaining = 18000L)
        val exp = sampleExport(listOf(job))
        val ct = BoostModifier(
            id = "ct",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower",
            village = Village.BUILDER_BASE,
            multiplier = 10.0,
            startTimestamp = t0,
            endTimestamp = t0 + 1800L,
        )
        val finish = engine.finishTime(job, exp, HelperLinks(), modifiers = listOf(ct))
        assertEquals(t0 + 1800L, finish)
    }

    @Test fun test28_zeroRemainingDuration() {
        val job = sampleJob(remaining = 0L)
        val exp = sampleExport(listOf(job))
        val finish = engine.finishTime(job, exp, HelperLinks())
        assertEquals(t0, finish)
        assertEquals(0L, engine.remainingAt(job, exp, HelperLinks(), t = t0))
    }

    @Test fun test29_negativeRemainingDurationClamped() {
        val job = sampleJob(remaining = -50L)
        val exp = sampleExport(listOf(job))
        val finish = engine.finishTime(job, exp, HelperLinks())
        assertEquals(t0, finish)
    }

    @Test fun test30_veryLongRemainingDuration() {
        val thirtyDays = 30L * 86400L
        val job = sampleJob(remaining = thirtyDays)
        val exp = sampleExport(listOf(job))
        val finish = engine.finishTime(job, exp, HelperLinks())
        assertEquals(t0 + thirtyDays, finish)
    }
}

