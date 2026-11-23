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
data class MaxStep(
    val seconds: Long, val cost: Long, val resource: Int, val townHall: Int,
    /** Hero equipment levels are paid in up to three ores: [cost] is the shiny ore (resource 6), these are the other two. */
    val glowyOre: Long = 0, val starryOre: Long = 0,
)

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
                    MaxStep(
                        a[0].jsonPrimitive.long, a[1].jsonPrimitive.long, a[2].jsonPrimitive.int, a[3].jsonPrimitive.int,
                        a.getOrNull(4)?.jsonPrimitive?.long ?: 0, a.getOrNull(5)?.jsonPrimitive?.long ?: 0,
                    )
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
    /** Hero equipment is paid in ore. */
    val shinyOre: Long = 0,
    val glowyOre: Long = 0,
    val starryOre: Long = 0,
) {
    operator fun plus(o: RoadTotals) = RoadTotals(
        seconds + o.seconds, gold + o.gold, elixir + o.elixir, darkElixir + o.darkElixir, goldOrElixir + o.goldOrElixir,
        shinyOre + o.shinyOre, glowyOre + o.glowyOre, starryOre + o.starryOre,
    )

    fun add(step: MaxStep) = RoadTotals(
        seconds + step.seconds,
        gold + if (step.resource == 0) step.cost else 0,
        elixir + if (step.resource == 1) step.cost else 0,
        darkElixir + if (step.resource == 2) step.cost else 0,
        goldOrElixir + if (step.resource == 3) step.cost else 0,
        shinyOre + if (step.resource == 6) step.cost else 0,
        glowyOre + step.glowyOre,
        starryOre + step.starryOre,
    )
}

/** Everything left to max the current Town Hall, split the way the card shows it. */
data class RoadToMax(
    val townHall: Int,
    /** Buildings other than defences: army, resource and helper buildings and the guardians (builders). */
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
    /** Defences and traps (builders); a group of their own, as in ClashKing. */
    val defences: RoadTotals = RoadTotals(),
    /** Crafted Defenses: the Crafting Station's modules (builders). */
    val crafted: RoadTotals = RoadTotals(),
    /** Traps (builders): a group of their own, as in ClashKing. */
    val traps: RoadTotals = RoadTotals(),
    /** Every item with the levels you have, for the lists that open under each card. */
    val items: List<RoadItem> = emptyList(),
    /** Shares of the work done, weighted by upgrade time (walls, which take no time, by cost): what the percentages show. */
    val work: RoadWork = RoadWork(),
    /** The laboratory's items split the way ClashKing shows them (their sum is [lab]), and hero equipment (ore, no timer). */
    val troops: RoadTotals = RoadTotals(),
    val spells: RoadTotals = RoadTotals(),
    val siege: RoadTotals = RoadTotals(),
    val equipment: RoadTotals = RoadTotals(),
) {
    /** Everything a builder does: buildings, defences and heroes. */
    val builderWork: RoadTotals get() = buildings + defences + crafted + traps + heroes

    /** Builder time when the work is shared across [builders], but never less than the longest single hero. */
    fun builderSeconds(builders: Int): Long =
        maxOf(builderWork.seconds / builders.coerceAtLeast(1), longestHeroSeconds)

    /** Heroes alone, with the same floor. */
    fun heroSeconds(builders: Int): Long =
        maxOf(heroes.seconds / builders.coerceAtLeast(1), longestHeroSeconds)

    /**
     * Share of the upgrade time already done across every group that has upgrade time (walls take none and are left out),
     * 0..1; null when there is nothing to do. Long upgrades count for more than short ones, as in ClashKing.
     */
    val overallDone: Double? get() = work.overall
}

/** The work done and left in one group: upgrade seconds, or for walls (no timer) resources. */
data class WorkShare(val total: Long = 0, val left: Long = 0) {
    operator fun plus(o: WorkShare) = WorkShare(total + o.total, left + o.left)

    /** 0..1, or null when the group has no work at this Town Hall. */
    val done: Double? get() = if (total > 0) ((total - left).toDouble() / total).coerceIn(0.0, 1.0) else null
}

data class RoadWork(
    val buildings: WorkShare = WorkShare(),
    val heroes: WorkShare = WorkShare(),
    val lab: WorkShare = WorkShare(),
    val pets: WorkShare = WorkShare(),
    val walls: WorkShare = WorkShare(),
    val defences: WorkShare = WorkShare(),
    val crafted: WorkShare = WorkShare(),
    val traps: WorkShare = WorkShare(),
    val troops: WorkShare = WorkShare(),
    val spells: WorkShare = WorkShare(),
    val siege: WorkShare = WorkShare(),
    val equipment: WorkShare = WorkShare(),
    /** Heroes by time, for the overall figure (the heroes card itself counts levels, as ClashKing does). */
    val heroTime: WorkShare = WorkShare(),
    /** The laboratory by time, for the overall figure (its cards count levels). */
    val labTime: WorkShare = WorkShare(),
) {
    /** Everything measured in time (all groups but walls) taken together. */
    val overall: Double? get() = (buildings + heroTime + labTime + pets + defences + crafted + traps).done
}

/** Levels owned against levels at the Town Hall limit, per group: what "done" is measured by (not owned counts as 0). */
data class RoadLevels(
    val buildings: LevelShare = LevelShare(),
    val heroes: LevelShare = LevelShare(),
    val lab: LevelShare = LevelShare(),
    val pets: LevelShare = LevelShare(),
    val walls: LevelShare = LevelShare(),
    val defences: LevelShare = LevelShare(),
    val crafted: LevelShare = LevelShare(),
    val traps: LevelShare = LevelShare(),
    val troops: LevelShare = LevelShare(),
    val spells: LevelShare = LevelShare(),
    val siege: LevelShare = LevelShare(),
    val equipment: LevelShare = LevelShare(),
)

/**
 * One item (all copies of it) for the lists under the cards: [group] is b buildings, d defences and traps, w walls,
 * h heroes, l troops, x spells, m siege machines, e hero equipment, t traps, p pets, c crafted defense modules; [levels] are the copies' levels, best first, with copies you do not have as 0.
 */
class RoadItem(val id: Int, val group: Char, val levels: List<Int>, val maxLevel: Int, val name: String? = null) {
    val count: Int get() = levels.size
    val lowest: Int get() = levels.minOrNull() ?: 0
    val levelsLeft: Int get() = levels.sumOf { (maxLevel - it).coerceAtLeast(0) }
    val maxed: Boolean get() = levelsLeft == 0
}

/** The sum of the levels you have and the sum of the levels you could have at this Town Hall. */
data class LevelShare(val have: Long = 0, val max: Long = 0) {
    operator fun plus(o: LevelShare) = LevelShare(have + o.have, max + o.max)

    /** 0..1, or null when there is nothing at this Town Hall. */
    val done: Double? get() = if (max > 0) (have.toDouble() / max).coerceIn(0.0, 1.0) else null
}

object RoadToMaxCalc {
    private const val CRAFTING_STATION = 1000097
    /** The Workshop's machines: the laboratory item ids that are siege machines, not troops. */
    private val SIEGE_MACHINES = setOf(4000051, 4000052, 4000062, 4000075, 4000087, 4000091, 4000092, 4000135, 4000188)
    /** Null when the export has no Town Hall, no owned items, or the bundled data is empty. */
    fun compute(data: MaxData, export: VillageExport): RoadToMax? {
        val townHall = export.townHallLevel?.coerceIn(1, data.maxTownHall.coerceAtLeast(1)) ?: return null
        if (data.items.isEmpty() || export.ownedLevels.isEmpty()) return null
        var buildings = RoadTotals(); var heroes = RoadTotals(); var lab = RoadTotals(); var pets = RoadTotals(); var walls = RoadTotals()
        var defences = RoadTotals(); var lDefences = LevelShare(); var crafted = RoadTotals(); var lCrafted = LevelShare(); val rows = mutableListOf<RoadItem>()
        var lBuildings = LevelShare(); var lHeroes = LevelShare(); var lLab = LevelShare(); var lPets = LevelShare(); var lWalls = LevelShare()
        var longestHero = 0L
        var wB = WorkShare(); var wH = WorkShare(); var wL = WorkShare(); var wP = WorkShare(); var wW = WorkShare(); var wD = WorkShare(); var wC = WorkShare(); var wT = WorkShare(); var wHT = WorkShare()
        var traps = RoadTotals(); var lTraps = LevelShare()
        var troops = RoadTotals(); var spells = RoadTotals(); var siege = RoadTotals(); var equipment = RoadTotals()
        var lTroops = LevelShare(); var lSpells = LevelShare(); var lSiege = LevelShare(); var lEquip = LevelShare()
        var wLT = WorkShare(); var wTroops = WorkShare(); var wSpells = WorkShare(); var wSiege = WorkShare(); var wEquip = WorkShare()
        for ((id, item) in data.items) {
            if (item.kind !in "bhlpwe") continue // Builder Base items (capital letters) are planned in Bedtime level up only
            val maxLevel = item.steps.indexOfLast { it.townHall <= townHall } + 1
            if (maxLevel == 0) continue
            // Crafting Station modules only count once the Crafting Station is in the village.
            if (item.kind == 'b' && item.category == 'c' && !export.ownedLevels.containsKey(CRAFTING_STATION)) continue
            val count = if (item.counts == null) 1 else item.counts.getOrElse(townHall) { item.counts.lastOrNull() ?: 0 }
            if (count == 0) continue
            // The best copies first: if the export lists more than allowed, the extra low ones are ignored.
            // An upgrade in progress is not done yet: the copy keeps its current level and the whole next level stays to do.
            val base = (export.ownedLevels[id] ?: emptyList()).toMutableList()
            for (real in export.upgradingLevels[id].orEmpty()) { val at = base.indexOf(real + 1); if (at >= 0) base[at] = real }
            val owned = base.sortedDescending().take(count).toMutableList()
            // Missing copies: unbuilt buildings start from nothing; troops and spells are unlocked at level 1 (free).
            val missingLevel = if (item.kind == 'l' || item.kind == 'e') 1 else 0
            // For the share done, a copy or a troop you do not have yet counts as level 0.
            val ownedSum = owned.sumOf { it.coerceAtMost(maxLevel).toLong() } // levels above this Town Hall's cap are not counted
            val shown = owned.toList() + List((count - owned.size).coerceAtLeast(0)) { 0 }
            while (owned.size < count) owned += missingLevel
            var total = RoadTotals()
            for (level in owned) {
                var chain = 0L
                for (i in level until maxLevel) { total = total.add(item.steps[i]); chain += item.steps[i].seconds }
                if (item.kind == 'h') longestHero = maxOf(longestHero, chain)
            }
            val share = LevelShare(ownedSum, owned.size.toLong() * maxLevel)
            // Work for the time share: every step of every copy, and what is left of it. Walls have no timer, so they count resources.
            fun weight(st: MaxStep) = if (item.kind == 'w' || item.kind == 'e') st.cost else st.seconds
            fun levelWeight(st: MaxStep) = if (item.kind == 'h' || item.kind == 'l' || item.kind == 'e') 1L else weight(st)
            var full = 0L; var fullLv = 0L
            for (i in missingLevel until maxLevel) { full += weight(item.steps[i]); fullLv += levelWeight(item.steps[i]) }
            full *= count; fullLv *= count
            var left = 0L; var leftLv = 0L
            for (level in owned) for (i in level until maxLevel) { left += weight(item.steps[i]); leftLv += levelWeight(item.steps[i]) }
            // Heroes, troops, spells, siege machines and equipment show the levels done (an item you do not have counts as level 0), as ClashKing does.
            val work = if (item.kind == 'h' || item.kind == 'l' || item.kind == 'e') WorkShare(share.max, share.max - share.have) else WorkShare(fullLv, leftLv.coerceAtMost(fullLv))
            val timeWork = WorkShare(full, left.coerceAtMost(full))
            // Traps (ids from 12,000,000) are a group of their own, as in ClashKing.
            val group = if (item.kind == 'b' && item.category in "dc") item.category
                else if (item.kind == 'b' && item.category == 'p' && id >= 12_000_000) 't'
                else if (item.kind == 'l' && id >= 26_000_000) 'x' // spells
                else if (item.kind == 'l' && id in SIEGE_MACHINES) 'm'
                else item.kind
            rows += RoadItem(id, group, shown, maxLevel, item.name)
            when (group) {
                'c' -> { crafted += total; lCrafted += share; wC += work }
                'd' -> { defences += total; lDefences += share; wD += work }
                'b' -> { buildings += total; lBuildings += share; wB += work }
                'h' -> { heroes += total; lHeroes += share; wH += work; wHT += timeWork }
                't' -> { traps += total; lTraps += share; wT += work }
                'l' -> { lab += total; lLab += share; wL += work; wLT += timeWork; troops += total; lTroops += share; wTroops += work }
                'x' -> { lab += total; lLab += share; wL += work; wLT += timeWork; spells += total; lSpells += share; wSpells += work }
                'm' -> { lab += total; lLab += share; wL += work; wLT += timeWork; siege += total; lSiege += share; wSiege += work }
                'e' -> { equipment += total; lEquip += share; wEquip += work }
                'p' -> { pets += total; lPets += share; wP += work }
                'w' -> { walls += total; lWalls += share; wW += work }
            }
        }
        return RoadToMax(townHall, buildings, heroes, lab, pets, walls, RoadLevels(lBuildings, lHeroes, lLab, lPets, lWalls, lDefences, lCrafted, lTraps, lTroops, lSpells, lSiege, lEquip),
            longestHero, defences, crafted, traps, rows, RoadWork(wB, wH, wL, wP, wW, wD, wC, wT, wTroops, wSpells, wSiege, wEquip, wHT, wLT),
            troops = troops, spells = spells, siege = siege, equipment = equipment)
    }
}
