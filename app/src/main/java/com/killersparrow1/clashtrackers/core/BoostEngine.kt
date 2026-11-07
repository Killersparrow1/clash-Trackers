package com.killersparrow1.clashtrackers.core

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class BoostModifierType(val village: Village, val defaultMultiplier: Double) {
    CLOCK_TOWER_FREE(Village.BUILDER_BASE, ClockTowerData.MULTIPLIER),
    CLOCK_TOWER_POTION(Village.BUILDER_BASE, ClockTowerData.MULTIPLIER),
    BUILDER_POTION(Village.HOME, 10.0),
    BUILDER_BITE(Village.HOME, 2.0),
    RESEARCH_POTION(Village.HOME, 24.0),
    PET_POTION(Village.HOME, 24.0),
    BUILDER_BOOST_PERK(Village.HOME, 1.0),
}

@Serializable
data class BoostModifier(
    val id: String,
    val type: BoostModifierType,
    val name: String,
    val village: Village = type.village,
    val multiplier: Double = type.defaultMultiplier,
    val startTimestamp: Long,
    val endTimestamp: Long,
) {
    init {
        require(multiplier >= 1.0) { "Multiplier must be ≥ 1.0" }
    }

    val durationSec: Long get() = (endTimestamp - startTimestamp).coerceAtLeast(0)

    fun isActiveAt(t: Long): Long? =
        if (startTimestamp < endTimestamp && t in startTimestamp until endTimestamp) endTimestamp - t else null

    fun appliesTo(job: Job): Boolean = when (type) {
        BoostModifierType.CLOCK_TOWER_FREE,
        BoostModifierType.CLOCK_TOWER_POTION ->
            job.category in setOf(JobCategory.BB_BUILDER, JobCategory.STAR_LAB)

        BoostModifierType.BUILDER_POTION,
        BoostModifierType.BUILDER_BITE ->
            job.category == JobCategory.BUILDER

        BoostModifierType.RESEARCH_POTION ->
            job.category == JobCategory.LAB

        BoostModifierType.PET_POTION ->
            job.category == JobCategory.PET

        BoostModifierType.BUILDER_BOOST_PERK ->
            job.category == JobCategory.BUILDER
    }

    fun extraRate(): Double = (multiplier - 1.0).coerceAtLeast(0.0)

    fun timeSavedSec(): Long = (durationSec * extraRate()).toLong()
}

@Serializable
data class ActiveBoostState(
    val clockTowerLevel: Int = ClockTowerData.DEFAULT_LEVEL,
    val modifiers: List<BoostModifier> = emptyList(),
    val goldPassPercent: Int = 0, // 0, 10, 15, 20
) {
    init {
        require(clockTowerLevel in ClockTowerData.MIN_LEVEL..ClockTowerData.MAX_LEVEL)
        require(goldPassPercent in setOf(0, 10, 15, 20))
    }

    fun activeModifiersAt(now: Long): List<BoostModifier> =
        modifiers.filter { it.endTimestamp > now }

    /**
     * Stacking rules:
     * - Clock Tower free boost and potion boost stack consecutively in time.
     * - Builder potions stack consecutively in time.
     * - Different types for the same village (or different villages) can run concurrently.
     */
    fun addClockTowerFree(tapTime: Long, level: Int = clockTowerLevel): ActiveBoostState {
        val duration = ClockTowerData.durationSecForLevel(level)
        val lastEnd = modifiers
            .filter { it.type in setOf(BoostModifierType.CLOCK_TOWER_FREE, BoostModifierType.CLOCK_TOWER_POTION) }
            .maxOfOrNull { it.endTimestamp }
        val start = if (lastEnd != null && lastEnd > tapTime) lastEnd else tapTime
        val mod = BoostModifier(
            id = "ct_free_${System.currentTimeMillis()}",
            type = BoostModifierType.CLOCK_TOWER_FREE,
            name = "Clock Tower (Lvl $level)",
            village = Village.BUILDER_BASE,
            multiplier = ClockTowerData.MULTIPLIER,
            startTimestamp = start,
            endTimestamp = start + duration,
        )
        return copy(modifiers = modifiers + mod)
    }

    fun addClockTowerPotion(tapTime: Long): ActiveBoostState {
        val duration = ClockTowerData.POTION_DURATION_SEC
        val lastEnd = modifiers
            .filter { it.type in setOf(BoostModifierType.CLOCK_TOWER_FREE, BoostModifierType.CLOCK_TOWER_POTION) }
            .maxOfOrNull { it.endTimestamp }
        val start = if (lastEnd != null && lastEnd > tapTime) lastEnd else tapTime
        val mod = BoostModifier(
            id = "ct_pot_${System.currentTimeMillis()}",
            type = BoostModifierType.CLOCK_TOWER_POTION,
            name = "Clock Tower Potion",
            village = Village.BUILDER_BASE,
            multiplier = ClockTowerData.MULTIPLIER,
            startTimestamp = start,
            endTimestamp = start + duration,
        )
        return copy(modifiers = modifiers + mod)
    }

    fun addBuilderPotion(tapTime: Long): ActiveBoostState {
        val duration = 3600L
        val lastEnd = modifiers
            .filter { it.type == BoostModifierType.BUILDER_POTION }
            .maxOfOrNull { it.endTimestamp }
        val start = if (lastEnd != null && lastEnd > tapTime) lastEnd else tapTime
        val mod = BoostModifier(
            id = "bpot_${System.currentTimeMillis()}",
            type = BoostModifierType.BUILDER_POTION,
            name = "Builder Potion",
            village = Village.HOME,
            multiplier = 10.0,
            startTimestamp = start,
            endTimestamp = start + duration,
        )
        return copy(modifiers = modifiers + mod)
    }

    fun removeModifier(id: String): ActiveBoostState =
        copy(modifiers = modifiers.filterNot { it.id == id })

    fun cancelActive(id: String, now: Long): ActiveBoostState {
        val mod = modifiers.find { it.id == id } ?: return this
        val rest = modifiers.filterNot { it.id == id }
        return if (mod.startTimestamp >= now) {
            copy(modifiers = rest)
        } else {
            copy(modifiers = rest + mod.copy(endTimestamp = now))
        }
    }
}

