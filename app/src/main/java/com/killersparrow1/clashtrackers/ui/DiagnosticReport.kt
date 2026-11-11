package com.killersparrow1.clashtrackers.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.SyncLog
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.core.formatTimestamp
import com.killersparrow1.clashtrackers.crash.CrashReporter
import com.killersparrow1.clashtrackers.notify.Notifier
import com.killersparrow1.clashtrackers.sync.ClockTowerTracking
import com.killersparrow1.clashtrackers.widget.WidgetAlarm
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import com.killersparrow1.clashtrackers.widget.WidgetUpdater

/** One row of the health checklist: a short label and whether it is fine. */
data class HealthCheck(val label: String, val ok: Boolean, val detail: String = "")

object DiagnosticReport {
    private const val LOG_LINES = 30
    private const val CRASH_LINES = 40

    /** Settings that commonly stop timers and alerts, checked right now. */
    fun health(context: Context, state: AppState): List<HealthCheck> {
        val iconCount = runCatching { WidgetIcons.get(context).available.size }.getOrDefault(0)
        val battery = runCatching {
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
        return listOf(
            HealthCheck("Saved village", state.export != null, if (state.export == null) "paste your export first" else ""),
            HealthCheck("Notifications allowed", Notifier.allowed(context), "finish alerts need this"),
            HealthCheck("Exact alarms allowed", WidgetAlarm.canScheduleExact(context), "alerts may be late without it"),
            HealthCheck("Battery: not restricted", battery, "restricted apps can stop updating"),
            HealthCheck("Icons bundled", iconCount > 0, "$iconCount files"),
        )
    }

    /** A plain-text summary for a GitHub issue. It never includes the player tag or name. */
    fun build(context: Context, state: AppState, model: WidgetModel, log: SyncLog, now: Long): String = buildString {
        appendLine("Clash Trackers diagnostic report")
        appendLine("App: ${appVersion(context)}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Time: ${formatTimestamp(now)}")
        appendLine()
        appendLine("Health")
        health(context, state).forEach { appendLine("  ${if (it.ok) "OK  " else "FAIL"} ${it.label}${if (it.detail.isNotEmpty() && !it.ok) " (${it.detail})" else ""}") }
        appendLine("  Clock Tower chip: ${if (ClockTowerTracking.enabled(context)) "on" else "off"}, alert: ${if (ClockTowerTracking.alertEnabled(context)) "on" else "off"}")
        appendLine()
        appendLine("Widgets")
        val count = runCatching { WidgetUpdater.widgetIds(context).size }.getOrDefault(-1)
        appendLine("  placed: $count")
        runCatching { widgetSizeReport(context, state) }.getOrNull()?.lines()?.filter { it.isNotBlank() }?.forEach { appendLine("  $it") }
        appendLine()
        appendLine("Village")
        val e = state.export
        if (e == null) appendLine("  none saved") else {
            appendLine("  Town Hall: ${e.townHallLevel ?: "?"}, jobs: ${e.jobs.size}, copied: ${formatTimestamp(e.timestamp)}")
            val boosts = listOfNotNull(
                e.builderBoostRemaining?.let { "builder potion ${it}s" },
                e.labBoostRemaining?.let { "research potion ${it}s" },
                e.petBoostRemaining?.let { "pet potion ${it}s" },
                e.builderBiteRemaining?.let { "builder bite ${it}s" },
                e.studySoupRemaining?.let { "study soup ${it}s" },
                e.clockTowerLevel?.let { "clock tower L$it" },
                e.clockTowerBoostRemaining?.let { "clock tower boost ${it}s" },
                e.clockTowerCooldownRemaining?.let { "clock tower cooldown ${it}s" },
            )
            appendLine("  Detected: ${if (boosts.isEmpty()) "no boosts" else boosts.joinToString(", ")}")
        }
        appendLine()
        appendLine("Widget text")
        widgetPreview(model).lines().forEach { appendLine("  $it") }
        appendLine()
        appendLine("Recent activity (newest first)")
        if (log.entries.isEmpty()) appendLine("  none")
        log.entries.take(LOG_LINES).forEach { appendLine("  ${it.line()}") }
        appendLine()
        appendLine("Last crash")
        val crash = CrashReporter.lastReport(context)
        if (crash == null) appendLine("  none saved") else crash.lines().take(CRASH_LINES).forEach { appendLine("  $it") }
    }
}
