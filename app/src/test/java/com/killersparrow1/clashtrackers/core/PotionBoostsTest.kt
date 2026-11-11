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
}
