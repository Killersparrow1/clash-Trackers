@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.formatTimestamp
import android.appwidget.AppWidgetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.killersparrow1.clashtrackers.data.StateRepository
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.RowButton
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.SyncLog
import com.killersparrow1.clashtrackers.core.SyncLogEntry
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.core.WidgetRow
import com.killersparrow1.clashtrackers.sync.SyncRunner
import com.killersparrow1.clashtrackers.widget.WidgetAlarm
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import com.killersparrow1.clashtrackers.widget.WidgetRenderer
import com.killersparrow1.clashtrackers.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SupportScreen(state: AppState, model: WidgetModel, log: SyncLog, timesCount: Int, now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var widgetCount by remember { mutableIntStateOf(-1) }
    var widgetSizes by remember { mutableStateOf("") }
    var refreshBusy by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    // Format strings resolved in the composable; callbacks only fill in the argument.
    val alarmSettingsErrorText = stringResource(R.string.support_alarm_settings_failed)
    val refreshFailedText = stringResource(R.string.support_refresh_failed)
    val exactAllowed = remember(now) { WidgetAlarm.canScheduleExact(context) }

    // The current state is a key: diagnostics never keep the initial, empty export captured.
    LaunchedEffect(state, now / 10) {
        widgetCount = runCatching { WidgetUpdater.widgetIds(context).size }.getOrDefault(-1)
        widgetSizes = runCatching { widgetSizeReport(context, state) }.getOrDefault("")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.help_title)) {
                Text(stringResource(R.string.help_summary), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.help_link)) }
            }
        }
        item {
            SectionCard(stringResource(R.string.support_last_crash)) { CrashSection() }
        }
        item {
            SectionCard(stringResource(R.string.support_widget_diagnostics)) {
                Text(when (widgetCount) {
                    -1 -> stringResource(R.string.support_widgets_unknown)
                    0 -> stringResource(R.string.support_widgets_none)
                    else -> stringResource(R.string.support_widgets_count, widgetCount)
                })
                if (widgetSizes.isNotEmpty()) Text(widgetSizes, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.support_icons_times, WidgetIcons.get(context).available.size, timesCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(if (exactAllowed) stringResource(R.string.support_exact_allowed) else stringResource(R.string.support_exact_not_allowed))
                if (!exactAllowed && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:${context.packageName}")),
                            )
                        }.onFailure { refreshError = alarmSettingsErrorText.format(it.message ?: "") }
                    }) { Text(stringResource(R.string.support_allow_exact)) }
                }
                refreshError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = {
                        refreshBusy = true
                        refreshError = null
                        scope.launch {
                            try {
                                widgetCount = SyncRunner.refreshWidgets(context, "app")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                refreshError = refreshFailedText.format(e.message ?: "")
                            } finally {
                                refreshBusy = false
                            }
                        }
                    },
                    enabled = !refreshBusy && widgetCount > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (refreshBusy) stringResource(R.string.support_refreshing) else stringResource(R.string.support_refresh_widget)) }
                if (widgetCount == 0) Text(stringResource(R.string.support_add_widget_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard(stringResource(R.string.support_saved_village)) {
                state.export?.let {
                    Text(stringResource(R.string.support_village_tag, it.tag))
                    Text(stringResource(R.string.support_export_copied, formatTimestamp(it.timestamp)))
                } ?: Text(stringResource(R.string.support_no_export))
            }
        }
        item {
            SectionCard(stringResource(R.string.support_widget_preview)) {
                SelectionContainer {
                    Text(widgetPreview(model), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            var activityOpen by rememberSaveable { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Collapsed by default: the whole header row toggles it, "+" closed / "−" open.
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { activityOpen = !activityOpen },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${stringResource(R.string.support_recent_activity)} (${log.entries.size})",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        Text(if (activityOpen) "−" else "+", style = MaterialTheme.typography.headlineMedium)
                    }
                    AnimatedVisibility(visible = activityOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (log.entries.isEmpty()) {
                                Text(stringResource(R.string.support_no_activity))
                            } else {
                                TextButton(
                                    onClick = { scope.launch { StateRepository(context.applicationContext).clearLog() } },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.support_clear_activity)) }
                            }
                            log.entries.forEach { entry ->
                                SelectionContainer {
                                    Text(
                                        SyncRunner.plainText(entry.line()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionCard(stringResource(R.string.support_about)) { AboutSection() } }
    }
    if (showHelp) HelpDialog(onDismiss = { showHelp = false })
}

private fun SyncLogEntry.line(): String {
    val mark = when (ok) { true -> "OK"; false -> "FAIL"; null -> "·" }
    return "${formatTimestamp(at)}  [$source] $mark  $message"
}

/** Each placed widget's size as the launcher reports it, and which row size it gets. */
private fun widgetSizeReport(context: android.content.Context, state: AppState): String {
    val r = context.resources
    val manager = AppWidgetManager.getInstance(context)
    val rows = (state.export?.jobs?.size ?: 0) + 2
    return WidgetUpdater.widgetIds(context).joinToString("\n") { id ->
        val o = manager.getAppWidgetOptions(id)
        val w = WidgetRenderer.reportedWidthDp(o)
        val h = WidgetRenderer.reportedHeightDp(o)
        val size = if (w != null && h != null) r.getString(R.string.support_size_dp, w, h)
            else r.getString(R.string.support_size_not_reported)
        val rowSize = if (WidgetRenderer.useCompactRows(o, rows)) r.getString(R.string.support_rows_compact)
            else r.getString(R.string.support_rows_normal)
        val need = r.getString(R.string.support_rows_need, rowSize, WidgetRenderer.normalHeightDp(rows))
        r.getString(R.string.support_widget_size_line, id, size, need)
    }
}

/** The widget model as text, so a rendering problem can be told apart from a data problem. */
private fun widgetPreview(m: WidgetModel): String = SyncRunner.plainText(buildString {
    appendLine("${m.title}   [Sync]")
    if (!m.hasData) {
        append(if (m.syncFailed) m.text.syncFailed else m.text.noData)
        return@buildString
    }
    fun line(r: WidgetRow) {
        val time = if (r.kind == RowKind.RUNNING) Countdown.text(r.remaining, m.text.day) else r.staticStatus
        val button = when (r.button) {
            RowButton.NONE -> ""
            RowButton.LINK -> " [+]"
            RowButton.APPRENTICE -> " [${m.text.apprenticeName.substringBefore(": ")}]"
            RowButton.LAB_ASSISTANT -> " [${m.text.assistantName.substringBefore(": ")}]"
        }
        val goblin = if (r.goblin) "${m.text.goblin} " else ""
        val label = if (r.kind == RowKind.RUNNING) r.label else r.name // finished rows: name only
        appendLine("  $goblin$label | $time | ${(r.progress * 100).toInt()}%$button")
    }
    val goblinPart = if (m.goblinWorking) " ${m.text.goblinSuffix}" else ""
    appendLine("${m.buildersLabel}$goblinPart   [${m.bite.text}] [${m.builderPotion.text}]")
    m.builderRows.forEach(::line)
    appendLine("LAB   [${m.researchPotion.text}]")
    m.labRows.forEach(::line)
    appendLine("PETS   [${m.petPotion.text}]")
    line(m.petRow)
    append(m.footerLeft.joinToString("") { it.text }).append("   ").append(m.footerRight)
})
