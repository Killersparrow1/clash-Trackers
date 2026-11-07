@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import com.killersparrow1.clashtrackers.core.formatTimestamp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.ActiveBoostState
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.EventSettings
import com.killersparrow1.clashtrackers.core.RowButton
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.SyncLogEntry
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.sync.SyncRunner

@Composable
fun TimerDashboard(
    model: WidgetModel,
    lastSync: SyncLogEntry?,
    activeBoostState: ActiveBoostState,
    eventSettings: EventSettings,
    now: Long,
    busy: Boolean,
    onSync: () -> Unit,
    onUpdateBoosts: ((ActiveBoostState) -> ActiveBoostState) -> Unit,
    onUpdateEvents: ((EventSettings) -> EventSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(TimerFilter.ALL) }
    val timers = remember(model, filter) { model.timers(filter) }

    Column(modifier.fillMaxSize()) {
        if (model.hasData) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimerFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(entry.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (model.hasData) {
                item(key = "village") { VillageSummary(model) }
            } else {
                item(key = "welcome") {
                    SectionCard("Your village, at a glance") {
                        Text("Bring your upgrades into one clear view.", style = MaterialTheme.typography.bodyLarge)
                        Text("1. Open Clash of Clans.")
                        Text("2. Go to Settings > Data Export > Copy.")
                        Text("3. Return here and sync from your clipboard.")
                        Text(
                            "No account or internet connection needed. Your village stays on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "sync") { SyncCard(lastSync, busy, onSync) }

            // Active Boosts & Events Card
            item(key = "boosts_events") {
                BoostEventSection(
                    activeBoostState = activeBoostState,
                    eventSettings = eventSettings,
                    now = now,
                    onUpdateBoosts = onUpdateBoosts,
                    onUpdateEvents = onUpdateEvents,
                )
            }

            if (model.hasData) {
                item(key = "heading") {
                    Text(
                        if (filter == TimerFilter.ALL) "Village upgrades" else "${filter.label} upgrades",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (timers.isEmpty()) {
                    item(key = "empty_filter") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                "No active timers in ${filter.label}.",
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(timers, key = { it.row.jobKey ?: "${it.category}_${it.row.name}" }) { timer ->
                    TimerCard(timer)
                }
            }
        }
    }
}

@Composable
private fun VillageSummary(model: WidgetModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.playerName, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(
                    model.townHall?.let { "Town Hall $it" },
                    "${model.buildersBusy} of ${model.buildersTotal} builders busy",
                    if (model.goblinWorking) "Goblin working" else null,
                    if (model.showBuilderBase) "Builder Base active" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            model.nextRunningTimer()?.let { next ->
                next.row.finishAt?.let { finishAt ->
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Next finish: ${next.row.name} (${Countdown.text(next.row.remaining)})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncCard(last: SyncLogEntry?, busy: Boolean, onSync: () -> Unit) {
    val failed = last?.ok == false
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        if (failed) "Sync failed" else if (last != null) "Last synced" else "Sync village",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    last?.let { Text(formatTimestamp(it.at), style = MaterialTheme.typography.bodySmall) }
                }
                if (last != null && !failed) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide details" else "Sync details")
                    }
                }
            }
            if (failed) {
                last?.let { SelectionContainer { Text(SyncRunner.plainText(it.message), style = MaterialTheme.typography.bodyMedium) } }
                Text("Copy fresh village data in Clash, then try again.", style = MaterialTheme.typography.bodyMedium)
            }
            if (last == null) {
                Text("Your clipboard is read only when you tap Sync.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSync,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    if (busy) "Syncing…" else if (failed) "Retry from clipboard" else "Sync from clipboard",
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
            if (last != null && !failed && showDetails) {
                SelectionContainer { Text(SyncRunner.plainText(last.message), style = MaterialTheme.typography.bodyMedium) }
                Text("Copy fresh data in Clash before syncing. The clipboard is read only when you tap Sync.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimerCard(timer: DashboardTimer) {
    val row = timer.row
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val categoryLabel = when (timer.category) {
                TimerFilter.BUILDER_BASE -> "Builder Base"
                TimerFilter.BUILDERS -> if (row.goblin) "Home Village · Goblin Builder" else "Home Village Builder"
                TimerFilter.LAB -> "Laboratory"
                TimerFilter.PETS -> "Pet House"
                else -> timer.category.label
            }
            Text(
                categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(SyncRunner.plainText(row.label), style = MaterialTheme.typography.titleLargeEmphasized)
            BasicText(
                if (row.kind == RowKind.RUNNING) Countdown.text(row.remaining) else row.staticStatus,
                style = MaterialTheme.typography.displaySmallEmphasized.copy(
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.primary,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 36.sp),
            )
            if (row.kind != RowKind.IDLE) {
                LinearWavyProgressIndicator(
                    progress = { row.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${(row.progress.coerceIn(0f, 1f) * 100).toInt()}% complete", style = MaterialTheme.typography.bodySmall)
            }
            if (row.kind == RowKind.RUNNING) {
                row.finishAt?.let {
                    Text("Finishes ${formatTimestamp(it)}", style = MaterialTheme.typography.bodyMedium)
                }
                val helper = when (row.button) {
                    RowButton.APPRENTICE -> "Builder's Apprentice assigned"
                    RowButton.LAB_ASSISTANT -> "Lab Assistant assigned"
                    else -> null
                }
                helper?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            } else {
                Text(
                    if (row.kind == RowKind.COMPLETED) "Ready for the next upgrade. Sync after starting it in the game."
                    else "No upgrade in the saved export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

