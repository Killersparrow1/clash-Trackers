package com.killersparrow1.clashtrackers.core

import java.time.Instant
import java.time.ZoneId

/** When the player sleeps, as minutes after midnight (local time). `start == end` means "never asleep". */
data class SleepWindow(val startMin: Int = 23 * 60, val endMin: Int = 7 * 60) {
    private fun minuteOfDay(epoch: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochSecond(epoch).atZone(zone)
        return t.hour * 60 + t.minute
    }

    fun asleep(epoch: Long, zone: ZoneId): Boolean {
        if (startMin == endMin) return false
        val m = minuteOfDay(epoch, zone)
        return if (startMin < endMin) m in startMin until endMin else m >= startMin || m < endMin
    }

    /** The next time (strictly after [epoch]) the clock reads [minute]. */
    private fun nextAt(epoch: Long, minute: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochSecond(epoch).atZone(zone)
        var day = now.toLocalDate()
        var candidate = day.atTime(minute / 60, minute % 60).atZone(zone)
        while (!candidate.toInstant().isAfter(now.toInstant())) {
            day = day.plusDays(1)
            candidate = day.atTime(minute / 60, minute % 60).atZone(zone)
        }
        return candidate.toEpochSecond()
    }

    /** [epoch] itself when awake, otherwise the moment the sleep window ends. */
    fun nextAwake(epoch: Long, zone: ZoneId): Long = if (asleep(epoch, zone)) nextAt(epoch, endMin, zone) else epoch

    /** The start of the next sleep window after [epoch]. */
    fun nextSleepStart(epoch: Long, zone: ZoneId): Long = nextAt(epoch, startMin, zone)
}

enum class PlanSlot { BUILDER, LAB, PET }

/** One upgrade the player could start in a free slot ([count] copies of the same item at the same level). */
data class PlanCandidate(
    val id: Int,
    /** The level it would be upgraded TO (1 = a new building or an unlock). */
    val level: Int,
    val count: Int,
    val seconds: Long,
    val cost: Long,
    /** 0 Gold, 1 Elixir, 2 Dark Elixir, 3 Gold or Elixir. */
    val resource: Int,
    val finishAt: Long,
)

/** What to start in a slot that frees at [freeAt] so it runs through the night and ends while the player is awake. */
data class BedtimePlan(val startAt: Long, val wakeAt: Long, val items: List<PlanCandidate>)

object Bedtime {
    private fun kinds(slot: PlanSlot): Set<Char> = when (slot) {
        PlanSlot.BUILDER -> setOf('b', 'h')
        PlanSlot.LAB -> setOf('l')
        PlanSlot.PET -> setOf('p')
    }

    /**
     * Base times only (no potions, helpers or Gold Pass), so finish times are estimates.
     * A candidate must still be running when the next sleep starts and finish while the player is awake.
     * Items already being upgraded are left out (one copy per running job).
     */
    fun plan(
        data: MaxData,
        export: VillageExport,
        slot: PlanSlot,
        freeAt: Long,
        window: SleepWindow,
        zone: ZoneId,
        limit: Int = 12,
    ): BedtimePlan? {
        val townHall = export.townHallLevel?.coerceIn(1, data.maxTownHall.coerceAtLeast(1)) ?: return null
        if (data.items.isEmpty() || export.ownedLevels.isEmpty()) return null
        val startAt = window.nextAwake(freeAt, zone)
        val wakeAt = window.nextAwake(window.nextSleepStart(startAt, zone), zone)
        val kinds = kinds(slot)
        val out = ArrayList<PlanCandidate>()
        for ((id, item) in data.items) {
            if (item.kind !in kinds) continue
            val maxLevel = item.steps.indexOfLast { it.townHall <= townHall } + 1
            if (maxLevel == 0) continue
            val count = if (item.counts == null) 1 else item.counts.getOrElse(townHall) { item.counts.lastOrNull() ?: 0 }
            if (count == 0) continue
            val owned = (export.ownedLevels[id] ?: emptyList()).sortedDescending().take(count).toMutableList()
            val missingLevel = if (item.kind == 'l') 1 else 0
            while (owned.size < count) owned += missingLevel
            // A copy that is being upgraded right now is listed at its next level: take it out of the candidates.
            for (job in export.jobs) if (job.dataId == id) owned.remove(job.targetLevel)
            for ((level, copies) in owned.groupingBy { it }.eachCount()) {
                if (level >= maxLevel) continue
                val step = item.steps[level]
                if (step.seconds <= 0) continue
                val finishAt = startAt + step.seconds
                if (finishAt < wakeAt || window.asleep(finishAt, zone)) continue
                out += PlanCandidate(id, level + 1, copies, step.seconds, step.cost, step.resource, finishAt)
            }
        }
        out.sortWith(compareBy({ it.finishAt }, { it.id }))
        return BedtimePlan(startAt, wakeAt, out.take(limit))
    }

    /** "12h", "14h 30m", "2d 4h". */
    fun durationLabel(seconds: Long, dayUnit: String = "d"): String {
        val days = seconds / 86_400
        val hours = seconds % 86_400 / 3600
        val minutes = seconds % 3600 / 60
        return when {
            days > 0 -> if (hours > 0) "$days$dayUnit ${hours}h" else "$days$dayUnit"
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            else -> "${minutes}m"
        }
    }
}
