package com.killersparrow1.clashtrackers.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** What it takes to get to level N of an item: time, cost and the resource the cost is paid in. */
data class MaxStep(val seconds: Long, val cost: Long, val resource: Int, val townHall: Int)

/**
 * One upgradeable item from the bundled `max_data.json`.
 * [kind]: b building/defense/trap/guardian, w wall, l lab (troop/spell/siege), h hero, p pet.
 * [counts] is how many may be owned at each Town Hall level (null = exactly one). `steps[i]` is the step TO level `i + 1`.
 */
class MaxItem(val kind: Char, val counts: List<Int>?, val steps: List<MaxStep>)

class MaxData(val maxTownHall: Int, val items: Map<Int, MaxItem>) {
    companion object {
        val EMPTY = MaxData(0, emptyMap())

        fun fromJson(text: String): MaxData {
            val root = Json.parseToJsonElement(text).jsonObject
            val items = root["items"]!!.jsonObject.entries.associate { (id, v) ->
                val o = v.jsonObject
                val counts = (o["n"] as? JsonArray)?.map { it.jsonPrimitive.int }
                val steps = o["l"]!!.jsonArray.map {
                    val a = it.jsonArray
                    MaxStep(a[0].jsonPrimitive.long, a[1].jsonPrimitive.long, a[2].jsonPrimitive.int, a[3].jsonPrimitive.int)
                }
                id.toInt() to MaxItem(o["k"]!!.jsonPrimitive.content.single(), counts, steps)
            }
            return MaxData(root["maxTh"]?.jsonPrimitive?.int ?: 0, items)
        }
    }
}

/** Time and the three resources for a group of upgrades. */
data class RoadTotals(
    val seconds: Long = 0,
    val gold: Long = 0,
    val elixir: Long = 0,
    val darkElixir: Long = 0,
    /** Walls can be paid in either Gold or Elixir. */
    val goldOrElixir: Long = 0,
) {
    operator fun plus(o: RoadTotals) = RoadTotals(
        seconds + o.seconds, gold + o.gold, elixir + o.elixir, darkElixir + o.darkElixir, goldOrElixir + o.goldOrElixir,
    )

    fun add(step: MaxStep) = RoadTotals(
        seconds + step.seconds,
        gold + if (step.resource == 0) step.cost else 0,
        elixir + if (step.resource == 1) step.cost else 0,
        darkElixir + if (step.resource == 2) step.cost else 0,
        goldOrElixir + if (step.resource == 3) step.cost else 0,
    )
}

/** Everything left to max the current Town Hall, split the way the card shows it. */
data class RoadToMax(
    val townHall: Int,
    /** Buildings, defenses, traps and guardians (builders). */
    val buildings: RoadTotals,
    val heroes: RoadTotals,
    /** Troops, spells and siege machines (laboratory). */
    val lab: RoadTotals,
    val pets: RoadTotals,
    val walls: RoadTotals,
) {
    /** Everything a builder does: buildings plus heroes. */
    val builderWork: RoadTotals get() = buildings + heroes
}

object RoadToMaxCalc {
    /** Null when the export has no Town Hall, no owned items, or the bundled data is empty. */
    fun compute(data: MaxData, export: VillageExport): RoadToMax? {
        val townHall = export.townHallLevel?.coerceIn(1, data.maxTownHall.coerceAtLeast(1)) ?: return null
        if (data.items.isEmpty() || export.ownedLevels.isEmpty()) return null
        var buildings = RoadTotals(); var heroes = RoadTotals(); var lab = RoadTotals(); var pets = RoadTotals(); var walls = RoadTotals()
        for ((id, item) in data.items) {
            val maxLevel = item.steps.indexOfLast { it.townHall <= townHall } + 1
            if (maxLevel == 0) continue
            val count = if (item.counts == null) 1 else item.counts.getOrElse(townHall) { item.counts.lastOrNull() ?: 0 }
            if (count == 0) continue
            // The best copies first: if the export lists more than allowed, the extra low ones are ignored.
            val owned = (export.ownedLevels[id] ?: emptyList()).sortedDescending().take(count).toMutableList()
            // Missing copies: unbuilt buildings start from nothing; troops and spells are unlocked at level 1 (free).
            val missingLevel = if (item.kind == 'l') 1 else 0
            while (owned.size < count) owned += missingLevel
            var total = RoadTotals()
            for (level in owned) for (i in level until maxLevel) total = total.add(item.steps[i])
            when (item.kind) {
                'b' -> buildings += total
                'h' -> heroes += total
                'l' -> lab += total
                'p' -> pets += total
                'w' -> walls += total
            }
        }
        return RoadToMax(townHall, buildings, heroes, lab, pets, walls)
    }
}
