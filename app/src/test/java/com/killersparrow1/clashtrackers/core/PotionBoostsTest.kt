package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The export's `boosts` object: builder, research and pet potions are read from the game's own remaining time. */
class PotionBoostsTest {

    private val base = SampleExports.text("export_4.json")

    /** export_4 with its `boosts` object swapped for [boosts] (fake player tag #TEST000, so safe to keep). */
    private fun withBoosts(boosts: String): String {
        val at = base.indexOf("\"boosts\":{")
        assertTrue(at > 0)
        return base.substring(0, at) + "\"boosts\":" + boosts + "}"
    }

    private fun sync(boosts: String): Pair<VillageExport, AppState> {
        val text = withBoosts(boosts)
        val out = SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced
        return out.export to out.state
    }

    @Test fun researchPotionIsRead() {
        val (export, state) = sync("""{"builder_boost":144,"lab_boost":3594,"clocktower_boost":287,"clocktower_cooldown":79487}""")
        assertEquals(144L, export.builderBoostRemaining)
        assertEquals(3594L, export.labBoostRemaining)
        assertNull(export.petBoostRemaining)
        val research = state.boosts.single { it.type == BoostType.RESEARCH_POTION }
        assertEquals(export.timestamp, research.start)
        assertEquals(export.timestamp + 3594L, research.end)
    }

    @Test fun petPotionIsMatchedByName() {
        val (export, state) = sync("""{"pet_boost":1200}""")
        assertEquals(1200L, export.petBoostRemaining)
        assertEquals(export.timestamp + 1200L, state.boosts.single { it.type == BoostType.PET_POTION }.end)
    }

    @Test fun builderBiteIsRead() {
        val (export, state) = sync("""{"builder_consumable":7192,"clocktower_cooldown":100}""")
        assertEquals(7192L, export.builderBiteRemaining)
        val bite = state.boosts.single { it.type == BoostType.BUILDER_BITE }
        assertEquals(export.timestamp, bite.start)
        assertEquals(export.timestamp + 7192L, bite.end)
    }

    @Test fun studySoupIsRead() {
        val (export, state) = sync("""{"lab_consumable":10784,"builder_consumable":7192}""")
        assertEquals(10784L, export.studySoupRemaining)
        val soup = state.boosts.single { it.type == BoostType.STUDY_SOUP }
        assertEquals(export.timestamp, soup.start)
        assertEquals(export.timestamp + 10784L, soup.end)
        // The Builder Bite is read from the same export, separately.
        assertEquals(7192L, export.builderBiteRemaining)
    }

    @Test fun noBoostsMeansNoPotions() {
        val (export, state) = sync("""{"clocktower_cooldown":100}""")
        assertNull(export.labBoostRemaining)
        assertNull(export.petBoostRemaining)
        assertNull(export.builderBiteRemaining)
        assertNull(export.studySoupRemaining)
        assertTrue(state.boosts.none { it.type == BoostType.RESEARCH_POTION || it.type == BoostType.PET_POTION || it.type == BoostType.BUILDER_BITE })
    }

    @Test fun petPotionAndClockTowerCooldownTogether() {
        val (export, _) = sync("""{"pet_boost":3586,"clocktower_cooldown":18148}""")
        assertEquals(3586L, export.petBoostRemaining)
        assertEquals(18148L, export.clockTowerCooldownRemaining)
        assertNull(export.clockTowerBoostRemaining)
        assertEquals(10, export.clockTowerLevel)
    }

    @Test fun clockTowerBoostAndCooldownAreRead() {
        val (export, _) = sync("""{"clocktower_boost":1808,"clocktower_cooldown":81008}""")
        assertEquals(1808L, export.clockTowerBoostRemaining)
        assertEquals(81008L, export.clockTowerCooldownRemaining)
    }

    @Test fun clockTowerZeroOrMissingValuesAreIgnored() {
        val (zero, _) = sync("""{"clocktower_boost":0,"clocktower_cooldown":0}""")
        assertNull(zero.clockTowerBoostRemaining)
        assertNull(zero.clockTowerCooldownRemaining)
        val (none, _) = sync("""{"builder_boost":100}""")
        assertNull(none.clockTowerCooldownRemaining)
        val (junk, _) = sync("""{"clocktower_cooldown":"soon","clocktower_boost":-5}""")
        assertNull(junk.clockTowerCooldownRemaining)
        assertNull(junk.clockTowerBoostRemaining)
    }

    @Test fun clockTowerTimersNeedTheBuilding() {
        val noTower = base.replace("\"data\":1000039", "\"data\":1999999")
        val text = noTower.substring(0, noTower.indexOf("\"boosts\":{")) + "\"boosts\":{\"clocktower_cooldown\":5000}}"
        val out = SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced
        assertNull(out.export.clockTowerLevel)
        assertNull(out.export.clockTowerCooldownRemaining)
    }

    @Test fun clockTowerReadyAlertFiresOnceWhenCooldownEnds() {
        val (export, state) = sync("""{"clocktower_cooldown":600}""")
        val settings = AppSettings()
        val names = NameMap(emptyMap())
        val end = export.timestamp + 600
        assertTrue(FinishNotices.check(state, settings, names, end - 5, true).notices.none { it.jobKey.startsWith("clocktower:") })
        val r = FinishNotices.check(state, settings, names, end + 5, true)
        assertEquals(1, r.notices.count { it.jobKey == "clocktower:$end" })
        val again = FinishNotices.check(state.copy(notified = r.notified), settings, names, end + 60, true)
        assertTrue(again.notices.none { it.jobKey.startsWith("clocktower:") })
        assertTrue(FinishNotices.check(state, settings, names, end + 5, false).notices.none { it.jobKey.startsWith("clocktower:") })
        assertEquals(end, FinishNotices.nextFinish(state, settings, end - 100, true)?.takeIf { it == end })
    }

    private fun pillAt(boosts: String, offset: Long): ClockTowerPill? {
        val (export, state) = sync(boosts)
        return WidgetModelBuilder.build(state, AppSettings(), SampleExports.names, SampleExports.times, export.timestamp + offset).clockTower
    }

    @Test fun clockTowerChipCountsDownThenStaysAsReady() {
        val counting = pillAt("""{"clocktower_cooldown":600}""", 100)!!
        assertTrue(!counting.ready)
        assertEquals(500L, counting.secondsLeft)
        val ready = pillAt("""{"clocktower_cooldown":600}""", 700)!!
        assertTrue(ready.ready)
        assertEquals("\u2713", ready.text)
        assertTrue(ready.highlighted)
    }

    @Test fun clockTowerWithNoCooldownIsReady() {
        assertTrue(pillAt("""{"builder_boost":100}""", 10)!!.ready)
    }
}
