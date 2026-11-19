package com.killersparrow1.clashtrackers.companion

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Settings for the Clash Companion (Settings > Import assistant). Off until the user turns it on. */
object CompanionPrefs {
    private const val PREFS = "companion"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = p(c).edit().putBoolean("enabled", on).apply()
    fun bar(c: Context) = p(c).getBoolean("bar", true)
    fun setBar(c: Context, on: Boolean) = p(c).edit().putBoolean("bar", on).apply()
    fun notification(c: Context) = p(c).getBoolean("notification", true)
    fun setNotification(c: Context, on: Boolean) = p(c).edit().putBoolean("notification", on).apply()

    /** Centre of the button as fractions of the screen; the default sits above the Build button, under Shop (landscape). */
    fun barPosition(c: Context): Pair<Float, Float> =
        p(c).getFloat("bar_x", 0.92f) to p(c).getFloat("bar_y", 0.655f)
    fun setBarPosition(c: Context, x: Float, y: Float) =
        p(c).edit().putFloat("bar_x", x.coerceIn(0f, 1f)).putFloat("bar_y", y.coerceIn(0f, 1f)).apply()

    /** True when the user has switched the accessibility service on in system settings. */
    fun serviceEnabled(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(c, CompanionService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
