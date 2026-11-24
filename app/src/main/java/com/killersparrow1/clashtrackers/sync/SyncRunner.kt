package com.killersparrow1.clashtrackers.sync

import android.content.ClipboardManager
import android.content.Context
import com.killersparrow1.clashtrackers.core.ExportParser
import com.killersparrow1.clashtrackers.core.ParseResult
import com.killersparrow1.clashtrackers.core.SyncLogic
import com.killersparrow1.clashtrackers.core.describe
import com.killersparrow1.clashtrackers.core.normalizeTag
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.data.StateRepository
import com.killersparrow1.clashtrackers.widget.WidgetUpdater
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One sync pipeline for both the widget's Sync button and the launcher screen's button. */
object SyncRunner {

    data class Result(val ok: Boolean, val message: String)

    /**
     * Reads the primary clip as text. Must be called while the calling activity has window focus
     * (Android 10+ only lets the focused app read the clipboard). Returns null if empty or blocked.
     */
    fun readClipboard(context: Context): String? = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    } catch (e: Exception) {
        null
    }

    /**
     * Parses [clipboard], saves the new state to the account matching export tag, logs the result and refreshes widgets.
     */
    suspend fun sync(context: Context, clipboard: String?, source: String): Result = withContext(NonCancellable) {
        val app = context.applicationContext
        val repo = StateRepository(app)
        val result = try {
            if (clipboard.isNullOrBlank()) {
                Result(false, "Sync failed: Clipboard is empty")
            } else {
                val export = when (val r = ExportParser.parse(clipboard)) {
                    is ParseResult.Invalid -> {
                        val msg = "Sync failed: Not village data: ${r.reason}"
                        repo.log(source, false, msg)
                        return@withContext Result(false, plainText(msg))
                    }
                    is ParseResult.Success -> r.export
                }
                val tag = normalizeTag(export.tag)
                val oldState = repo.getAccountState(tag)
                val outcome = SyncLogic.apply(oldState, clipboard)

                repo.updateAccountState(tag) { outcome.state }
                repo.ensureAccountAndSelect(tag)

                val ok = outcome is SyncLogic.Outcome.Synced
                Result(ok, plainText(outcome.describe(Names.get(app))))
            }
        } catch (t: Throwable) {
            Result(false, "Error: ${t.javaClass.simpleName}: ${t.message}")
        }
        repo.log(source, result.ok, result.message)
        refreshWidgets(app, source)
        result
    }

    /** Re-renders every placed widget; logs failures and how many widgets are placed. */
    suspend fun refreshWidgets(context: Context, source: String): Int {
        val count = WidgetUpdater.updateAll(context, source)
        if (count == 0) StateRepository(context).log(source, null, "No Clash Trackers widget is placed on the home screen")
        return count
    }

    /**
     * Text shown by system UI (toasts, the launcher screen) uses the phone's font, which draws "→"
     * as "ʼn" on a HiOS (Tecno) phone (the APK's strings are correct UTF-8). Use ASCII there.
     */
    fun plainText(s: String): String = s.replace(" → ", " -> ").replace("→", "->")
}
