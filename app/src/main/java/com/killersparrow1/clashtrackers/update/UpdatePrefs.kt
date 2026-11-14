package com.killersparrow1.clashtrackers.update

import android.content.Context

/** Whether to look for updates when the app opens, when it last did, and a version the user said "Later" to. */
object UpdatePrefs {
    private const val PREFS = "app_update"
    private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheck(c: Context) = p(c).getBoolean("auto", true)
    fun setAutoCheck(c: Context, on: Boolean) = p(c).edit().putBoolean("auto", on).apply()

    /** True at most every 12 hours; marks the check as done. */
    fun dueNow(c: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!autoCheck(c)) return false
        val last = p(c).getLong("last", 0L)
        if (nowMs - last in 0 until CHECK_EVERY_MS) return false
        p(c).edit().putLong("last", nowMs).apply()
        return true
    }

    fun skipped(c: Context, version: String) = p(c).getString("skipped", null) == version
    fun skip(c: Context, version: String) = p(c).edit().putString("skipped", version).apply()
}
