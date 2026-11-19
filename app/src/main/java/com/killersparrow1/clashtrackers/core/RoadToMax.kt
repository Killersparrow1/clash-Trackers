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
 * [category]: d defense, a army (army buildings, troops, spells, siege, heroes, pets), r resource, p permanent (traps, guardians, other).
 * [counts] is how many may be owned at each Town Hall level (null = exactly one). `steps[i]` is the step TO level `i + 1`.
 */
class MaxItem(
    val kind: Char,
    val counts: List<Int>?,
    val steps: List<MaxStep>,
    val category: Char = 'p',
    /** English name, for items added after the app was built (the bundled name files don't know them). */
    val name: String? = null,
    /** Where the picture is on assets.clashk.ing, used when the app has no bundled icon for the item. */
    val icon: String? = null,
)

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
                id.toInt() to MaxItem(
                    o["k"]!!.jsonPrimitive.content.single(), counts, steps,
                    o["c"]?.jsonPrimitive?.content?.singleOrNull() ?: 'p',
                    o["nm"]?.jsonPrimitive?.content,
                    o["i"]?.jsonPrimitive?.content,
                )
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
    /** Levels owned against levels at the Town Hall limit, per group. */
    val levels: RoadLevels = RoadLevels(),
    /** Time the hero with the most work left needs. A hero is upgraded one level after another, so more builders cannot beat it. */
    val longestHeroSeconds: Long = 0,
) {
    /** Everything a builder does: buildings plus heroes. */
    val builderWork: RoadTotals get() = buildings + heroes

    /** Builder time when the work is shared across [builders], but never less than the longest single hero. */
    fun builderSeconds(builders: Int): Long =
        maxOf(builderWork.seconds / builders.coerceAtLeast(1), longestHeroSeconds)

    /** Heroes alone, with the same floor. */
    fun heroSeconds(builders: Int): Long =
        maxOf(heroes.seconds / builders.coerceAtLeast(1), longestHeroSeconds)

    /** The average of the groups' shares done, 0..1; null when there is nothing to do. */
    val overallDone: Double? get() = listOfNotNull(
        levels.buildings.done, levels.walls.done, levels.heroes.done, levels.lab.done, levels.pets.done,
    ).takeIf { it.isNotEmpty() }?.average()
}

/** Levels owned against levels at the Town Hall limit, per group: what "done" is measured by (not owned counts as 0). */
data class RoadLevels(
    val buildings: LevelShare = LevelShare(),
    val heroes: LevelShare = LevelShare(),
    val lab: LevelShare = LevelShare(),
    val pets: LevelShare = LevelShare(),
    val walls: LevelShare = LevelShare(),
)

/** The sum of the levels you have and the sum of the levels you could have at this Town Hall. */
data class LevelShare(val have: Long = 0, val max: Long = 0) {
    operator fun plus(o: LevelShare) = LevelShare(have + o.have, max + o.max)

    /** 0..1, or null when there is nothing at this Town Hall. */
    val done: Double? get() = if (max > 0) (have.toDouble() / max).coerceIn(0.0, 1.0) else null
}

object RoadToMaxCalc {
    /** Null when the export has no Town Hall, no owned items, or the bundled data is empty. */
    fun compute(data: MaxData, export: VillageExport): RoadToMax? {
        val townHall = export.townHallLevel?.coerceIn(1, data.maxTownHall.coerceAtLeast(1)) ?: return null
        if (data.items.isEmpty() || export.ownedLevels.isEmpty()) return null
        var buildings = RoadTotals(); var heroes = RoadTotals(); var lab = RoadTotals(); var pets = RoadTotals(); var walls = RoadTotals()
        var lBuildings = LevelShare(); var lHeroes = LevelShare(); var lLab = LevelShare(); var lPets = LevelShare(); var lWalls = LevelShare()
        var longestHero = 0L
        for ((id, item) in data.items) {
            if (item.kind !in "bhlpw") continue // Builder Base items (capital letters) are planned in Bedtime level up only
            val maxLevel = item.steps.indexOfLast { it.townHall <= townHall } + 1
            if (maxLevel == 0) continue
            val count = if (item.counts == null) 1 else item.counts.getOrElse(townHall) { item.counts.lastOrNull() ?: 0 }
            if (count == 0) continue
            // The best copies first: if the export lists more than allowed, the extra low ones are ignored.
            val owned = (export.ownedLevels[id] ?: emptyList()).sortedDescending().take(count).toMutableList()
            // Missing copies: unbuilt buildings start from nothing; troops and spells are unlocked at level 1 (free).
            val missingLevel = if (item.kind == 'l') 1 else 0
            // For the share done, a copy or a troop you do not have yet counts as level 0.
            val ownedSum = owned.sumOf { it.toLong() }
            while (owned.size < count) owned += missingLevel
            var total = RoadTotals()
            for (level in owned) {
                var chain = 0L
                for (i in level until maxLevel) { total = total.add(item.steps[i]); chain += item.steps[i].seconds }
                if (item.kind == 'h') longestHero = maxOf(longestHero, chain)
            }
            val share = LevelShare(ownedSum, owned.size.toLong() * maxLevel)
            when (item.kind) {
                'b' -> { buildings += total; lBuildings += share }
                'h' -> { heroes += total; lHeroes += share }
                'l' -> { lab += total; lLab += share }
                'p' -> { pets += total; lPets += share }
                'w' -> { walls += total; lWalls += share }
            }
        }
        return RoadToMax(townHall, buildings, heroes, lab, pets, walls, RoadLevels(lBuildings, lHeroes, lLab, lPets, lWalls), longestHero)
    }
}
