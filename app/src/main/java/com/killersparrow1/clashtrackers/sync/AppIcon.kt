package com.killersparrow1.clashtrackers.sync

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Which launcher icon the app shows. "Themed" (the default) lets the phone's themed-icons setting tint it;
 * "colour" never does. Two launcher entries in the manifest, exactly one enabled. Android may close the app or
 * briefly show both entries on some launchers when this changes.
 */
object AppIcon {
    private const val THEMED = "com.killersparrow1.clashtrackers.IconThemed"
    private const val COLOUR = "com.killersparrow1.clashtrackers.IconColour"

    /** True unless the colour-only entry has been switched on. */
    fun themed(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, COLOUR)) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setThemed(context: Context, themed: Boolean) {
        val pm = context.packageManager
        // Enable the new entry first so the app never has no launcher icon.
        val (on, off) = if (themed) THEMED to COLOUR else COLOUR to THEMED
        pm.setComponentEnabledSetting(ComponentName(context, on), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(ComponentName(context, off), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
