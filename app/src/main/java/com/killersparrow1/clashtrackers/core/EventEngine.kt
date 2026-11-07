package com.killersparrow1.clashtrackers.core

import kotlinx.serialization.Serializable

@Serializable
enum class Village {
    HOME,
    BUILDER_BASE,
    BOTH,
}

@Serializable
enum class EventType {
    HAMMER_JAM,
    BUILDER_BASE_BONANZA,
    CUSTOM,
}

@Serializable
data class EventModifier(
    val id: String,
    val name: String,
    val type: EventType,
    val village: Village,
    val timeReductionPercent: Double = 50.0,
    val costReductionPercent: Double = 50.0,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val enabled: Boolean = true,
    val eligibleCategories: Set<JobCategory> = emptySet(),
) {
    init {
        require(timeReductionPercent in 0.0..99.0) { "Time reduction must be in [0, 99)%" }
        require(costReductionPercent in 0.0..100.0) { "Cost reduction must be in [0, 100]%" }
    }

    fun isActiveAt(t: Long): Boolean =
        enabled && startTimestamp < endTimestamp && t in startTimestamp until endTimestamp

    fun appliesTo(job: Job): Boolean {
        if (!enabled || startTimestamp >= endTimestamp) return false
        val matchesVillage = when (village) {
            Village.HOME -> job.category in setOf(JobCategory.BUILDER, JobCategory.LAB, JobCategory.PET)
            Village.BUILDER_BASE -> job.category in setOf(JobCategory.BB_BUILDER, JobCategory.STAR_LAB)
            Village.BOTH -> true
        }
        if (!matchesVillage) return false
        return eligibleCategories.isEmpty() || job.category in eligibleCategories
    }

    /**
     * Converts a time reduction percentage into an effective progression speed multiplier.
     * E.g., a 50% reduction means an upgrade finishes in half the time, equivalent to 2.0× progression rate
     * (extraRate = 1.0).
     */
    fun effectiveMultiplier(): Double {
        val fraction = timeReductionPercent / 100.0
        return if (fraction >= 0.99) 100.0 else 1.0 / (1.0 - fraction)
    }

    fun extraRate(): Double = (effectiveMultiplier() - 1.0).coerceAtLeast(0.0)
}

@Serializable
data class EventSettings(
    val events: List<EventModifier> = defaultEvents(),
) {
    companion object {
        fun defaultEvents(): List<EventModifier> {
            val now = System.currentTimeMillis() / 1000
            val day = 86400L
            return listOf(
                EventModifier(
                    id = "hammer_jam",
                    name = "Hammer Jam",
                    type = EventType.HAMMER_JAM,
                    village = Village.HOME,
                    timeReductionPercent = 50.0,
                    costReductionPercent = 50.0,
                    startTimestamp = now,
                    endTimestamp = now + 14 * day,
                    enabled = false,
                    eligibleCategories = setOf(JobCategory.BUILDER),
                ),
                EventModifier(
                    id = "bb_bonanza",
                    name = "Builder Base Bonanza",
                    type = EventType.BUILDER_BASE_BONANZA,
                    village = Village.BUILDER_BASE,
                    timeReductionPercent = 50.0,
                    costReductionPercent = 0.0,
                    startTimestamp = now,
                    endTimestamp = now + 7 * day,
                    enabled = false,
                    eligibleCategories = setOf(JobCategory.BB_BUILDER, JobCategory.STAR_LAB),
                ),
            )
        }
    }
}

