package com.killersparrow1.clashtrackers.ui

import android.content.Context

/**
 * Whether the app may ask for the (optional) accessibility service. "Remind me later" pauses the question for a week,
 * "Don't ask again" stops it until the person turns it back on in Settings. Stored in the app's own preferences, so it
 * survives restarts and updates. Only the reminder is affected: the service itself, and its switch in Settings, still work.
 */
object AccessibilityReminder {
    private const val PREFS = "accessibility_reminder"
    private const val SNOOZE_MS = 7L * 24 * 3_600_000

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when the person chose "Don't ask again". */
    fun never(c: Context): Boolean = p(c).getBoolean("never", false)

    /** When "Remind me later" runs out (0 when it was not chosen). */
    fun snoozedUntil(c: Context): Long = p(c).getLong("until", 0L)

    fun snoozed(c: Context, now: Long = System.currentTimeMillis()): Boolean = snoozedUntil(c) > now

    /** The reminder may be shown. */
    fun shouldAsk(c: Context, now: Long = System.currentTimeMillis()): Boolean = allowed(never(c), snoozedUntil(c), now)

    /** The rule on its own: never asked again, or still inside the "Remind me later" week, means no. */
    internal fun allowed(never: Boolean, snoozedUntil: Long, now: Long): Boolean = !never && snoozedUntil <= now

    internal fun snoozeEnd(now: Long): Long = now + SNOOZE_MS

    fun remindLater(c: Context, now: Long = System.currentTimeMillis()) {
        p(c).edit().putLong("until", snoozeEnd(now)).apply()
    }

    fun neverAsk(c: Context) {
        p(c).edit().putBoolean("never", true).apply()
    }

    /** Back to asking (Settings). */
    fun reset(c: Context) {
        p(c).edit().remove("never").remove("until").apply()
    }
}
