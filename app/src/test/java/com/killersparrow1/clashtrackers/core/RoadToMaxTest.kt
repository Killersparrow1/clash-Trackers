package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoadToMaxTest {
    // Cannon: up to 2 copies from Town Hall 2; steps to level 1/2/3 need Town Hall 1/2/3. Level 3 is not allowed at TH2.
    // Barbarian (lab): level 1 free, level 2 costs Elixir and needs TH2. King (hero): steps to level 1..2 need TH1.
    // Wall: instant, paid in Gold or Elixir.
    private val data = MaxData.fromJson(
        """{"maxTh":3,"items":{
          "1":{"k":"b","n":[0,0,2,2],"l":[[100,10,0,1],[200,20,0,2],[400,40,0,3]]},
          "2":{"k":"l","n":null,"l":[[0,0,1,1],[1000,500,1,2],[2000,900,1,3]]},
          "3":{"k":"h","n":null,"l":[[10,0,2,1],[20,5,2,1]]},
          "4":{"k":"w","n":[0,10,10,10],"l":[[0,100,3,1],[0,200,3,2]]}
        }}""",
    )

    private fun export(th: Int?, owned: Map<Int, List<Int>>) = VillageExport(
        tag = "#T", timestamp = 0, townHallLevel = th, builderHutCount = 5, helpers = emptyList(), jobs = emptyList(),
        ownedLevels = owned,
    )

    @Test fun unbuiltCopyCostsEveryStepUpToTheTownHallLimit() {
        // TH2: cannons max level 2, 2 allowed. One at level 1, none other: step to 2 (200 s) + a full new build (100 + 200 s).
        val r = RoadToMaxCalc.compute(data, export(2, mapOf(1 to listOf(1))))!!
        assertEquals(200L + 100L + 200L, r.buildings.seconds)
        assertEquals(20L + 10L + 20L, r.buildings.gold)
    }

    @Test fun upgradingCopyCountsAsItsNextLevel() {
        val r = RoadToMaxCalc.compute(data, export(2, mapOf(1 to listOf(2, 2))))!!
        assertEquals(0L, r.buildings.seconds)
    }

    @Test fun labItemsStartFromLevelOneAndSplitByResource() {
        // Not in the export: unlocked at level 1, so only the step to level 2 (TH2) remains.
        val r = RoadToMaxCalc.compute(data, export(2, mapOf(1 to listOf(2, 2))))!!
        assertEquals(1000L, r.lab.seconds)
        assertEquals(500L, r.lab.elixir)
        assertEquals(0L, r.lab.gold)
    }

    @Test fun heroesCountAsBuilderWorkAndUseDarkElixir() {
        val r = RoadToMaxCalc.compute(data, export(2, mapOf(3 to listOf(1), 1 to listOf(2, 2))))!!
        assertEquals(20L, r.heroes.seconds)
        assertEquals(5L, r.heroes.darkElixir)
        assertEquals(20L, r.builderWork.seconds)
    }

    @Test fun wallsAreKeptSeparate() {
        val r = RoadToMaxCalc.compute(data, export(2, mapOf(4 to List(10) { 1 }, 1 to listOf(2, 2))))!!
        assertEquals(10 * 200L, r.walls.goldOrElixir)
        assertEquals(0L, r.buildings.goldOrElixir)
    }

    @Test fun townHallBeyondTheDataIsClampedAndNothingOwnedMeansNoCard() {
        assertNotNull(RoadToMaxCalc.compute(data, export(9, mapOf(1 to listOf(1)))))
        assertNull(RoadToMaxCalc.compute(data, export(2, emptyMap())))
        assertNull(RoadToMaxCalc.compute(data, export(null, mapOf(1 to listOf(1)))))
    }

    @Test fun bundledDataParsesAndIsConsistent() {
        val file = java.io.File("src/main/assets/max_data.json")
        org.junit.Assume.assumeTrue(file.isFile)
        val real = MaxData.fromJson(file.readText())
        org.junit.Assert.assertTrue(real.items.size > 100)
        org.junit.Assert.assertTrue(real.maxTownHall >= 16)
        org.junit.Assert.assertTrue(real.items.values.all { it.steps.isNotEmpty() })
    }
}
