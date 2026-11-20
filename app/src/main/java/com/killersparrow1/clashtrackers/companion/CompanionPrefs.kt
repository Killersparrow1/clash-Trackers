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

    /** Where the user dragged the button to: distance of its centre from the right edge (in screen heights) and its height fraction. */
    fun position(c: Context): Pair<Float, Float> = p(c).getFloat("pos_right", 0.195f) to p(c).getFloat("pos_y", 0.635f)
    fun setPosition(c: Context, rightOffset: Float, y: Float) =
        p(c).edit().putFloat("pos_right", rightOffset.coerceIn(0f, 1.5f)).putFloat("pos_y", y.coerceIn(0f, 1f)).apply()

    /** True when the user has switched the accessibility service on in system settings. */
    fun serviceEnabled(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(c, CompanionService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
