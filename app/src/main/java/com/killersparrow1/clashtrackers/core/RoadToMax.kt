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
    /** The same groups counted from nothing to the Town Hall limit: what "done" is measured against. */
    val full: RoadFull = RoadFull(),
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

    /** Share of the timed work (builders, laboratory, pets) already done, 0..1; null when there is none. */
    val overallDone: Double? get() = RoadProgress.done(
        buildings + heroes + lab + pets,
        full.buildings + full.heroes + full.lab + full.pets,
    )
}

/** Totals from nothing to the Town Hall limit, per group. */
data class RoadFull(
    val buildings: RoadTotals = RoadTotals(),
    val heroes: RoadTotals = RoadTotals(),
    val lab: RoadTotals = RoadTotals(),
    val pets: RoadTotals = RoadTotals(),
    val walls: RoadTotals = RoadTotals(),
)

object RoadProgress {
    private fun cost(t: RoadTotals) = t.gold + t.elixir + t.darkElixir + t.goldOrElixir

    /**
     * How much of a group is done, 0..1: by time where upgrades take time, by total cost where they do not (walls).
     * Null when the group has nothing to do at this Town Hall.
     */
    fun done(remaining: RoadTotals, total: RoadTotals): Double? {
        val fraction = when {
            total.seconds > 0 -> 1.0 - remaining.seconds.toDouble() / total.seconds
            cost(total) > 0 -> 1.0 - cost(remaining).toDouble() / cost(total)
            else -> return null
        }
        return fraction.coerceIn(0.0, 1.0)
    }
}

object RoadToMaxCalc {
    /** Null when the export has no Town Hall, no owned items, or the bundled data is empty. */
    fun compute(data: MaxData, export: VillageExport): RoadToMax? {
        val townHall = export.townHallLevel?.coerceIn(1, data.maxTownHall.coerceAtLeast(1)) ?: return null
        if (data.items.isEmpty() || export.ownedLevels.isEmpty()) return null
        var buildings = RoadTotals(); var heroes = RoadTotals(); var lab = RoadTotals(); var pets = RoadTotals(); var walls = RoadTotals()
        var fBuildings = RoadTotals(); var fHeroes = RoadTotals(); var fLab = RoadTotals(); var fPets = RoadTotals(); var fWalls = RoadTotals()
        var longestHero = 0L
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
            for (level in owned) {
                var chain = 0L
                for (i in level until maxLevel) { total = total.add(item.steps[i]); chain += item.steps[i].seconds }
                if (item.kind == 'h') longestHero = maxOf(longestHero, chain)
            }
            // The same item from nothing (a copy not yet built, or a troop at level 1), for the "done" share.
            var full = RoadTotals()
            repeat(count) { for (i in missingLevel until maxLevel) full = full.add(item.steps[i]) }
            when (item.kind) {
                'b' -> { buildings += total; fBuildings += full }
                'h' -> { heroes += total; fHeroes += full }
                'l' -> { lab += total; fLab += full }
                'p' -> { pets += total; fPets += full }
                'w' -> { walls += total; fWalls += full }
            }
        }
        return RoadToMax(townHall, buildings, heroes, lab, pets, walls, RoadFull(fBuildings, fHeroes, fLab, fPets, fWalls), longestHero)
    }
}
