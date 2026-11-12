package com.killersparrow1.clashtrackers.core

import java.time.Instant
import java.time.ZoneOffset

/**
 * Gold Pass time reductions the player set by hand (the game's export doesn't include them).
 * Only used for estimates the export can't give, such as a job's full length for its progress bar:
 * the exported remaining time already has the reduction in it, so it is never applied to that again.
 */
data class GoldPassReduction(val builderPercent: Int = 0, val researchPercent: Int = 0) {
    /** Percent off for a job, 0 where the Gold Pass doesn't apply (pets, Builder Base). */
    fun percentFor(category: JobCategory): Int = when (category) {
        JobCategory.BUILDER -> builderPercent
        JobCategory.LAB -> researchPercent
        else -> 0
    }

    /** [base] seconds with the reduction taken off: 10 days at 20% is 8 days. */
    fun reduce(base: Long, category: JobCategory): Long {
        val p = percentFor(category).coerceIn(0, MAX_PERCENT)
        return Math.round(base * (100 - p) / 100.0)
    }

    companion object {
        val NONE = GoldPassReduction()
        val CHOICES = listOf(0, 10, 15, 20)
        private const val MAX_PERCENT = 20
    }
}

object GoldPassLogic {
    /** The Gold Pass season is a calendar month: "2026-11". */
    fun monthKey(epochSec: Long): String {
        val d = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC)
        return "%04d-%02d".format(d.year, d.monthValue)
    }

    /** Ask once per month: when nothing is saved for the current month yet. */
    fun needsAsk(savedMonth: String?, currentMonth: String, askEnabled: Boolean): Boolean =
        askEnabled && savedMonth != currentMonth

    /**
     * Season days (1 = the 1st) when the next builder-boost step is expected to unlock. Not confirmed against the
     * game, so it only drives a dismissible reminder, never the numbers on the timers.
     */
    val TIER_DAYS = listOf(4 to 15, 8 to 20)

    /** 1-based day of the month (UTC) for [epochSec]. */
    fun dayOfSeason(epochSec: Long): Int = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).dayOfMonth

    /** The builder percent worth asking about today, or null when the player is already there or has no Gold Pass. */
    fun bannerTarget(day: Int, gold: Boolean, builderPercent: Int): Int? {
        if (!gold) return null
        val target = TIER_DAYS.lastOrNull { day >= it.first }?.second ?: return null
        return target.takeIf { it > builderPercent }
    }
}
