package com.killersparrow1.clashtrackers.core

/**
 * The plan for the sixth builder (B.O.B.), after ClashClock's guide: three gear-ups (Cannon, Archer Tower, Mortar), any
 * Star Laboratory troop at level 18, a Builder Base defence at level 9, Battle Machine + Battle Copter levels adding up to 45,
 * and B.O.B Control at level 5 (Builder Hall 9). The requirement list and the gear-up price and time come from that guide;
 * levels, upgrade times and costs come from the game data, and a done gear-up is read from the export.
 */
enum class BobKind { GEAR_UP, TROOP, DEFENCE, HEROES, CONTROL }

/**
 * One requirement. [have] and [need] are levels (for heroes the combined level). [seconds] and [cost] are what is still to do;
 * [resource] is 0 Gold or 4 Builder Gold. [itemId] is the gear-up building, the quickest troop or defence, or B.O.B Control.
 * [needsBuilderHall] is the Builder Hall level some of the steps wait for, when yours is lower.
 */
data class BobRow(
    val kind: BobKind,
    val itemId: Int,
    val done: Boolean,
    val have: Int,
    val need: Int,
    val seconds: Long = 0,
    val cost: Long = 0,
    val resource: Int = 4,
    val needsBuilderHall: Int? = null,
    /** HEROES only: the Battle Machine and Battle Copter levels that reach the total in the least time. */
    val machineTarget: Int = 0,
    val copterTarget: Int = 0,
)

data class BobPlan(val unlocked: Boolean, val builderHall: Int, val rows: List<BobRow>) {
    val doneCount: Int get() = if (unlocked) rows.size else rows.count { it.done }
    val remainingSeconds: Long get() = rows.filter { !it.done }.sumOf { it.seconds }
    val builderGold: Long get() = rows.filter { !it.done && it.resource == 4 }.sumOf { it.cost }
    val gold: Long get() = rows.filter { !it.done && it.resource == 0 }.sumOf { it.cost }
    /** The first requirement not done yet. */
    val next: BobRow? get() = if (unlocked) null else rows.firstOrNull { !it.done }
}

object BobPlanner {
    const val TROOP_LEVEL = 18
    const val DEFENCE_LEVEL = 9
    const val HERO_TOTAL = 45
    const val CONTROL_LEVEL = 5
    const val CONTROL_BUILDER_HALL = 9

    const val BOB_CONTROL = 1000065
    const val BATTLE_MACHINE = 28000003
    const val BATTLE_COPTER = 28000005

    /** Home-village building -> gear-up Gold price and days, from ClashClock's guide (the game data has no gear-up price or time). */
    private val GEAR_UPS = listOf(
        Triple(1000008, 1_000_000L, 2L), // Cannon
        Triple(1000009, 3_000_000L, 7L), // Archer Tower
        Triple(1000013, 6_000_000L, 14L), // Mortar
    )

    /** Seconds, Builder Gold and the highest Builder Hall wanted, for going from [from] to [to] (levels) of [item]. */
    private fun climb(item: MaxItem, from: Int, to: Int): Triple<Long, Long, Int> {
        var seconds = 0L; var cost = 0L; var hall = 0
        for (i in from until to.coerceAtMost(item.steps.size)) {
            seconds += item.steps[i].seconds; cost += item.steps[i].cost; hall = maxOf(hall, item.steps[i].townHall)
        }
        return Triple(seconds, cost, hall)
    }

    /** Null when the village has no Builder Base. */
    fun compute(data: MaxData, export: VillageExport): BobPlan? {
        val bh = Bedtime.builderHall(export) ?: return null
        fun owned(id: Int) = export.ownedLevels[id].orEmpty()
        fun gate(hall: Int) = hall.takeIf { it > bh }
        val rows = ArrayList<BobRow>()

        for ((id, gold, days) in GEAR_UPS) {
            val done = id in export.gearedUp
            rows += BobRow(BobKind.GEAR_UP, id, done, if (done) 1 else 0, 1, if (done) 0 else days * 86_400, if (done) 0 else gold, resource = 0)
        }

        // Any Star Laboratory troop at level 18: the one you already have there, or the one that gets there fastest.
        val troops = data.items.filter { it.value.kind == 'S' && owned(it.key).isNotEmpty() && it.value.steps.size >= TROOP_LEVEL }
        val bestTroop = troops.minByOrNull { (id, item) ->
            val have = owned(id).max()
            if (have >= TROOP_LEVEL) -1L else climb(item, have, TROOP_LEVEL).first
        }
        rows += if (bestTroop == null) BobRow(BobKind.TROOP, 0, false, 0, TROOP_LEVEL) else {
            val have = owned(bestTroop.key).max()
            val (s, c, h) = climb(bestTroop.value, have, TROOP_LEVEL)
            BobRow(BobKind.TROOP, bestTroop.key, have >= TROOP_LEVEL, have, TROOP_LEVEL, s, c, needsBuilderHall = gate(h))
        }

        // A Builder Base defence at level 9.
        val defences = data.items.filter { it.value.kind == 'B' && it.value.category == 'd' && owned(it.key).isNotEmpty() && it.value.steps.size >= DEFENCE_LEVEL }
        val bestDefence = defences.minByOrNull { (id, item) ->
            val have = owned(id).max()
            if (have >= DEFENCE_LEVEL) -1L else climb(item, have, DEFENCE_LEVEL).first
        }
        rows += if (bestDefence == null) BobRow(BobKind.DEFENCE, 0, false, 0, DEFENCE_LEVEL) else {
            val have = owned(bestDefence.key).max()
            val (s, c, h) = climb(bestDefence.value, have, DEFENCE_LEVEL)
            BobRow(BobKind.DEFENCE, bestDefence.key, have >= DEFENCE_LEVEL, have, DEFENCE_LEVEL, s, c, needsBuilderHall = gate(h))
        }

        // Battle Machine + Battle Copter: try every split and keep the one that needs the least upgrade time.
        val machine = data.items[BATTLE_MACHINE]; val copter = data.items[BATTLE_COPTER]
        val bm = owned(BATTLE_MACHINE).maxOrNull() ?: 0; val bc = owned(BATTLE_COPTER).maxOrNull() ?: 0
        rows += if (machine == null || copter == null) BobRow(BobKind.HEROES, BATTLE_MACHINE, false, bm + bc, HERO_TOTAL)
        else if (bm + bc >= HERO_TOTAL) BobRow(BobKind.HEROES, BATTLE_MACHINE, true, bm + bc, HERO_TOTAL, machineTarget = bm, copterTarget = bc)
        else {
            var best: BobRow? = null
            for (a in bm..machine.steps.size) {
                val b = maxOf(bc, HERO_TOTAL - a)
                if (b > copter.steps.size) continue
                val (s1, c1, h1) = climb(machine, bm, a)
                val (s2, c2, h2) = climb(copter, bc, b)
                val row = BobRow(BobKind.HEROES, BATTLE_MACHINE, false, bm + bc, HERO_TOTAL, s1 + s2, c1 + c2, needsBuilderHall = gate(maxOf(h1, h2)), machineTarget = a, copterTarget = b)
                if (best == null || row.seconds < best.seconds) best = row
            }
            best ?: BobRow(BobKind.HEROES, BATTLE_MACHINE, false, bm + bc, HERO_TOTAL)
        }

        // B.O.B Control level 5, which needs Builder Hall 9.
        val control = data.items[BOB_CONTROL]
        val level = owned(BOB_CONTROL).maxOrNull() ?: 0
        rows += if (control == null) BobRow(BobKind.CONTROL, BOB_CONTROL, false, level, CONTROL_LEVEL) else {
            val (s, c, _) = climb(control, level, CONTROL_LEVEL)
            BobRow(BobKind.CONTROL, BOB_CONTROL, level >= CONTROL_LEVEL, level, CONTROL_LEVEL, s, c, needsBuilderHall = gate(CONTROL_BUILDER_HALL))
        }
        return BobPlan(export.hasBobsHut, bh, rows)
    }
}
