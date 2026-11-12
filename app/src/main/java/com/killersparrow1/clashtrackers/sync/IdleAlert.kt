package com.killersparrow1.clashtrackers.sync

import android.content.Context

/** "Remind me when a builder or the lab sits free" (Settings > Notifications). Off by default. */
object IdleAlert {
    private const val PREFS = "idle_alert"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOURS = "hours"

    /** Hours a builder or the lab may sit free before the reminder. */
    val CHOICES = listOf(1, 2, 3, 6, 12)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun hours(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOURS, 2).takeIf { it in CHOICES } ?: 2

    fun setHours(context: Context, hours: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_HOURS, hours).apply()
    }

    /** Seconds to wait, or null when the reminder is off. */
    fun afterSec(context: Context): Long? = if (enabled(context)) hours(context) * 3600L else null
}
