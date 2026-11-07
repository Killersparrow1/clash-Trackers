package com.killersparrow1.clashtrackers.core

import kotlinx.serialization.Serializable

/**
 * Official Clock Tower specifications for Builder Base (Builder Hall 4+).
 * At each level, Clock Tower provides a 10× speed boost for a set duration once available.
 */
object ClockTowerData {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10
    const val DEFAULT_LEVEL = 9
    const val MULTIPLIER = 10.0
    /** Extra rate over 1× speed (i.e. 9× extra speed). */
    const val EXTRA_RATE = 9.0

    /** Clock Tower Potion provides 30 minutes of 10× boost. */
    const val POTION_DURATION_SEC = 30 * 60L

    /**
     * Duration in seconds for a given Clock Tower level (1 to 10).
     * Level 1: 14 min (840 s) ... Level 10: 32 min (1920 s). Formula: (12 + 2 * level) * 60.
     */
    fun durationSecForLevel(level: Int): Long {
        val lvl = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return (12 + 2 * lvl) * 60L
    }

    /** Time saved in seconds from running a boost of [durationSec] at 10× speed. */
    fun timeSavedSec(durationSec: Long): Long = durationSec * (MULTIPLIER.toLong() - 1L)
}

