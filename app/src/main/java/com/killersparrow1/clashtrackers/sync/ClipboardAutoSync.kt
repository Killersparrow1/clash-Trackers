package com.killersparrow1.clashtrackers.sync

import android.content.Context
import com.killersparrow1.clashtrackers.core.ExportParser
import com.killersparrow1.clashtrackers.core.ParseResult

/**
 * "Syncs from the clipboard when the app opens ". Only village data that parses as a Clash of Clans export
 * is used, and each copied export is used once, so other clipboard text is ignored and an old export is
 * never pasted over newer data a second time.
 */
object ClipboardAutoSync {
    private const val PREFS = "clipboard_auto_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST = "last_hash"

    /** On by default; the only place to turn it off is Developer options. */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** Marks [clipboard] as already used (a manual Sync counts too). */
    fun markUsed(context: Context, clipboard: String?) {
        if (clipboard.isNullOrBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_LAST, clipboard.hashCode()).apply()
    }

    /** The clipboard text to sync, or null when the switch is off, it is not new, or it is not village data. */
    fun pending(context: Context, clipboard: String?): String? {
        if (!enabled(context) || clipboard.isNullOrBlank()) return null
        val trimmed = clipboard.trim()
        if (!trimmed.startsWith("{")) return null // cheap check before parsing arbitrary text
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_LAST) && prefs.getInt(KEY_LAST, 0) == clipboard.hashCode()) return null
        return if (ExportParser.parse(clipboard) is ParseResult.Success) clipboard else null
    }
}
