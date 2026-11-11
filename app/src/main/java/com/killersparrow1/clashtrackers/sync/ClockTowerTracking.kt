package com.killersparrow1.clashtrackers.sync

import android.content.Context

/** "Show the Clock Tower cooldown on the widget" switch (Settings). On by default; only matters when the export has a Clock Tower. */
object ClockTowerTracking {
    private const val PREFS = "clock_tower_tracking"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    private const val KEY_ALERT = "alert"

    /** Notify when the Clock Tower cooldown ends (needs finish alerts on as well). On by default. */
    fun alertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ALERT, true)

    fun setAlertEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALERT, on).apply()
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
