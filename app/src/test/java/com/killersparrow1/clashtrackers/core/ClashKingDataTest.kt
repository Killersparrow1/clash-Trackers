package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The conversion of ClashKing's `static_data.json` into the app's game data, on a small made-up file in the same shape. */
class ClashKingDataTest {
    private fun building(id: Long, name: String, levels: String, type: String = "Defense", village: String = "home", resource: String = "Gold") =
        """{"_id":$id,"name":"$name","type":"$type","village":"$village","upgrade_resource":"$resource","levels":[$levels]}"""

    private fun bLevel(level: Int, time: Long, cost: Long, th: Int?) =
        """{"level":$level,"build_time":$time,"build_cost":$cost${th?.let { ""","required_townhall":$it""" } ?: ""}}"""

    private fun lLevel(level: Int, time: Long, cost: Long, th: Int?) =
        """{"level":$level,"upgrade_time":$time,"upgrade_cost":$cost${th?.let { ""","required_townhall":$it""" } ?: ""}}"""

    /** Padding so the file has enough items to count as game data. */
    private fun filler() = (1..25).joinToString(",") { building(2000000L + it, "Filler $it", bLevel(1, 10, 100, 1) + "," + bLevel(2, 20, 200, 2)) }

    private fun file(extraBuildings: String = "", troops: String = "", walls: String = "", unlocks: String = """{"_id":1000008,"quantity":2}""") = """
        {"buildings":[
          {"_id":1000001,"name":"Town Hall","type":"Town Hall","village":"home","levels":[
            {"level":1,"unlocks":[$unlocks]},{"level":2,"unlocks":[{"_id":1000008,"quantity":1}]},{"level":3}]},
          ${filler()}${if (extraBuildings.isNotEmpty()) ",$extraBuildings" else ""}${if (walls.isNotEmpty()) ",$walls" else ""}],
         "traps":[],"troops":[${troops}],"spells":[],"heroes":[],"pets":[],"guardians":[]}"""

    private fun convert(text: String, previous: MaxData? = null) = MaxData.fromJson(ClashKingData.convert(text, previous))

    @Test fun buildingStepsAreTheCostToReachEachLevel() {
        val d = convert(file(extraBuildings = building(1000008, "Cannon", bLevel(1, 60, 250, 1) + "," + bLevel(2, 600, 1000, 2) + "," + bLevel(3, 3600, 4000, 3))))
        val cannon = d.items.getValue(1000008)
        assertEquals(listOf(MaxStep(60, 250, 0, 1), MaxStep(600, 1000, 0, 2), MaxStep(3600, 4000, 0, 3)), cannon.steps)
        assertEquals('b', cannon.kind)
        assertEquals('d', cannon.category)
        assertEquals("Cannon", cannon.name)
        assertEquals("buildings/home-village/cannon/level_3.webp", cannon.icon)
        assertEquals(3, d.maxTownHall)
        // 2 at Town Hall 1, one more at 2, none added at 3
        assertEquals(listOf(0, 2, 3, 3), cannon.counts)
    }

    @Test fun researchStepsAreShiftedByOneLevel() {
        val troop = """{"_id":4000000,"name":"Barbarian","village":"home","upgrade_resource":"Elixir","levels":[
            ${lLevel(1, 0, 100, 1)},${lLevel(2, 60, 200, 2)},${lLevel(3, 0, 0, 3)}]}"""
        val d = convert(file(troops = troop))
        val barb = d.items.getValue(4000000)
        // level 1 is unlocked; 1 -> 2 costs what level 1 says and needs level 2's Town Hall
        assertEquals(listOf(MaxStep(0, 0, 1, 1), MaxStep(0, 100, 1, 2), MaxStep(60, 200, 1, 3)), barb.steps)
        assertEquals('l', barb.kind)
        assertEquals("troops/barbarian/icon.webp", barb.icon)
    }

    @Test fun thingsThePlayerDoesNotUpgradeAreLeftOut() {
        val superTroop = """{"_id":4000001,"name":"Super Barbarian","village":"home","upgrade_resource":"Elixir","levels":[${lLevel(1, 0, 0, 1)},${lLevel(2, 0, 0, 2)}]}"""
        val builder = """{"_id":4000033,"name":"Beta Minion","village":"builderBase","upgrade_resource":"Gold","levels":[${lLevel(1, 10, 100, 1)},${lLevel(2, 0, 0, 2)}]}"""
        val d = convert(file(troops = "$superTroop,$builder"))
        assertNull(d.items[4000001])
        assertNull(d.items[4000033])
    }

    @Test fun wallsCanBePaidInGoldOrElixirFromLevelFive() {
        val levels = (1..6).joinToString(",") { bLevel(it, 0, it * 1000L, 1) }
        val d = convert(file(walls = building(1000010, "Wall", levels, type = "Wall")))
        val wall = d.items.getValue(1000010)
        assertEquals('w', wall.kind)
        assertEquals(listOf(0, 0, 0, 0, 3, 3), wall.steps.map { it.resource })
    }

    @Test fun aMissingTownHallRequirementBecomesTheNewestTownHall() {
        val d = convert(file(extraBuildings = building(1000107, "Spiky Tower", bLevel(1, 100, 500, null) + "," + bLevel(2, 200, 900, null))))
        assertEquals(listOf(3, 3), d.items.getValue(1000107).steps.map { it.townHall })
    }

    @Test fun aBuildingListedWithoutLevelsKeepsItsOldSteps() {
        val old = convert(file(extraBuildings = building(1000093, "Helper Hut", bLevel(1, 5, 50, 1))))
        val fresh = convert(file(extraBuildings = building(1000093, "Helper Hut", "")), previous = old)
        assertEquals(old.items.getValue(1000093).steps, fresh.items.getValue(1000093).steps)
        assertNull(convert(file(extraBuildings = building(1000093, "Helper Hut", ""))).items[1000093])
    }

    @Test fun notClashKingDataIsRefused() {
        for (text in listOf("", "{", "[]", "<html></html>", "{}", """{"buildings":[]}""", file().replace("\"_id\":1000001", "\"_id\":1000099"))) {
            try {
                ClashKingData.convert(text)
                fail("accepted: ${text.take(30)}")
            } catch (e: Exception) { /* expected */ }
        }
        // too few items to be real game data
        try {
            ClashKingData.convert("""{"buildings":[{"_id":1000001,"name":"Town Hall","type":"Town Hall","village":"home","levels":[{"level":1}]}],"traps":[],"troops":[],"spells":[],"heroes":[],"pets":[],"guardians":[]}""")
            fail("accepted a file with no items")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun fileNamesFollowClashKingsRule() {
        assertEquals("p_e_k_k_a".replace("_", ""), ClashKingData.clean("P.E.K.K.A"))
        assertEquals("builder's_hut", ClashKingData.clean("Builder's Hut"))
        assertEquals("l_a_s_s_i".replace("_", ""), ClashKingData.clean("L.A.S.S.I"))
        assertEquals("spells/lightning_spell.webp", ClashKingData.iconPath("spells", "Lightning Spell", 5))
        assertEquals("heroes/archer_queen/icon.webp", ClashKingData.iconPath("heroes", "Archer Queen", 5))
    }

    @Test fun sameDataIgnoresSpacingAndOrderButNotFewerTownHalls() {
        assertTrue(ClashKingData.sameData("""{"a":1,"b":[1,2]}""", """{ "b": [1, 2],  "a": 1 }"""))
        assertFalse(ClashKingData.sameData("""{"a":1}""", """{"a":2}"""))
        assertFalse(ClashKingData.sameData("""{"a":1}""", null))
        assertFalse(ClashKingData.sameData("nope", "nope")) // not JSON: never "the same", so it is not skipped silently
        val a = ClashKingData.convert(file())
        val data = MaxData.fromJson(a)
        assertFalse(ClashKingData.isBetter(a, data, data, a))
        assertNotNull(a)
        val older = MaxData(data.maxTownHall + 1, data.items)
        assertFalse(ClashKingData.isBetter("""{"x":1}""", data, older, a)) // fewer Town Halls than in use
        assertTrue(ClashKingData.isBetter("""{"x":1}""", data, data, a)) // different, same Town Hall
    }
}
