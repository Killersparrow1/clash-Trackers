package com.killersparrow1.clashtrackers.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Option: items you have not unlocked yet are drawn in black and white in the Road to max lists. On by default. */
object LockedLook {
    private const val PREFS = "road_to_max_look"
    private const val KEY = "grey_locked"

    /** Read by the lists, so they redraw as soon as the setting changes. */
    var greyLocked by mutableStateOf(true)
        private set

    fun init(context: Context) {
        greyLocked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun set(context: Context, on: Boolean) {
        greyLocked = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
