@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.compose.ui.res.stringResource
import com.killersparrow1.clashtrackers.core.formatTimestamp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.ActiveBoostState
import com.killersparrow1.clashtrackers.core.BoostModifier
import com.killersparrow1.clashtrackers.core.ClockTowerData
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.EventModifier
import com.killersparrow1.clashtrackers.core.EventSettings
import com.killersparrow1.clashtrackers.core.EventType
import com.killersparrow1.clashtrackers.core.Village
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoostEventSection(
    activeBoostState: ActiveBoostState,
    eventSettings: EventSettings,
    now: Long,
    onUpdateBoosts: ((ActiveBoostState) -> ActiveBoostState) -> Unit,
    onUpdateEvents: ((EventSettings) -> EventSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeModifiers = remember(activeBoostState, now) {
        activeBoostState.activeModifiersAt(now)
    }
    val activeEvents = remember(eventSettings, now) {
        eventSettings.events.filter { it.isActiveAt(now) }
    }

    var showClockTowerDialog by rememberSaveable { mutableStateOf(false) }
    var showEventConfigDialog by rememberSaveable { mutableStateOf<EventModifier?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.boosts_title), style = MaterialTheme.typography.titleMediumEmphasized)
                if (activeModifiers.isNotEmpty() || activeEvents.isNotEmpty()) {
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text(stringResource(R.string.boosts_active, activeModifiers.size + activeEvents.size), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Active Boosts List
            if (activeModifiers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeModifiers.forEach { mod ->
                        ActiveBoostItem(mod = mod, now = now, onCancel = {
                            onUpdateBoosts { it.cancelActive(mod.id, now) }
                        })
                    }
                }
            }

            // Active Events Banner
            if (activeEvents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeEvents.forEach { event ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(event.name, style = MaterialTheme.typography.labelLarge)
                                    val leftSec = (event.endTimestamp - now).coerceAtLeast(0)
                                    Text(
                                        stringResource(R.string.event_speedup, event.timeReductionPercent.toInt(), Countdown.text(leftSec, stringResource(R.string.time_day_short))),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { showEventConfigDialog = event }) {
                                    Text(stringResource(R.string.action_configure))
                                }
                            }
                        }
                    }
                }
            }

            if (activeModifiers.isEmpty() && activeEvents.isEmpty()) {
                Text(
                    stringResource(R.string.boosts_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quick Actions FlowRow
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { showClockTowerDialog = true }) {
                    Text(stringResource(R.string.clock_tower))
                }
                FilledTonalButton(onClick = {
                    onUpdateBoosts { it.addBuilderPotion(now) }
                }) {
                    Text(stringResource(R.string.add_builder_potion))
                }
                FilledTonalButton(onClick = {
                    onUpdateBoosts { it.addClockTowerPotion(now) }
                }) {
                    Text(stringResource(R.string.add_ct_potion))
                }
            }
        }
    }

    // Clock Tower Free Boost & Level Dialog
    if (showClockTowerDialog) {
        ClockTowerDialog(
            currentLevel = activeBoostState.clockTowerLevel,
            onDismiss = { showClockTowerDialog = false },
            onActivate = { selectedLevel ->
                onUpdateBoosts { state ->
                    state.copy(clockTowerLevel = selectedLevel).addClockTowerFree(now, selectedLevel)
                }
                showClockTowerDialog = false
            },
        )
    }

    // Event Configuration Dialog
    showEventConfigDialog?.let { event ->
        EventConfigDialog(
            event = event,
            onDismiss = { showEventConfigDialog = null },
            onSave = { updated ->
                onUpdateEvents { settings ->
                    settings.copy(events = settings.events.map { if (it.id == updated.id) updated else it })
                }
                showEventConfigDialog = null
            },
        )
    }
}

@Composable
private fun ActiveBoostItem(mod: BoostModifier, now: Long, onCancel: () -> Unit) {
    val leftSec = (mod.endTimestamp - now).coerceAtLeast(0)
    val savedSec = mod.timeSavedSec()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(mod.name, style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.boost_left, Countdown.text(leftSec, stringResource(R.string.time_day_short)), mod.multiplier.toInt(), formatDuration(savedSec)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun ClockTowerDialog(
    currentLevel: Int,
    onDismiss: () -> Unit,
    onActivate: (Int) -> Unit,
) {
    var level by remember { mutableIntStateOf(currentLevel) }
    val durationMin = (ClockTowerData.durationSecForLevel(level) / 60).toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ct_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ct_dialog_desc))
                Text(stringResource(R.string.ct_dialog_level, level, durationMin), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.roundToInt() },
                    valueRange = ClockTowerData.MIN_LEVEL.toFloat()..ClockTowerData.MAX_LEVEL.toFloat(),
                    steps = ClockTowerData.MAX_LEVEL - ClockTowerData.MIN_LEVEL - 1,
                )
                Text(stringResource(R.string.ct_dialog_saved, (durationMin * 9) / 60, (durationMin * 9) % 60), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onActivate(level) }) {
                Text(stringResource(R.string.ct_activate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EventConfigDialog(
    event: EventModifier,
    onDismiss: () -> Unit,
    onSave: (EventModifier) -> Unit,
) {
    var enabled by remember { mutableStateOf(event.enabled) }
    var reductionPercent by remember { mutableStateOf(event.timeReductionPercent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.event_enable))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(stringResource(R.string.event_reduction, reductionPercent.toInt()), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = reductionPercent.toFloat(),
                    onValueChange = { reductionPercent = it.toDouble() },
                    valueRange = 10f..80f,
                    steps = 13,
                )
                Text("Village: ${event.village.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                Text("Window: ${formatTimestamp(event.startTimestamp)} — ${formatTimestamp(event.endTimestamp)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(event.copy(enabled = enabled, timeReductionPercent = reductionPercent))
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

