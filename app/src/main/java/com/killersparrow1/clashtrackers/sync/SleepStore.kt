package com.killersparrow1.clashtrackers.sync

import android.content.Context
import com.killersparrow1.clashtrackers.core.SleepWindow

/** When the player sleeps, for the Plan tab's Bedtime view (this phone only, not shared with widgets). */
object SleepStore {
    private const val PREFS = "plan_sleep"
    private const val KEY_START = "start"
    private const val KEY_END = "end"

    fun get(context: Context): SleepWindow {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = SleepWindow()
        return SleepWindow(
            p.getInt(KEY_START, d.startMin).coerceIn(0, 1439),
            p.getInt(KEY_END, d.endMin).coerceIn(0, 1439),
        )
    }

    fun set(context: Context, window: SleepWindow) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_START, window.startMin).putInt(KEY_END, window.endMin).apply()
    }
}
