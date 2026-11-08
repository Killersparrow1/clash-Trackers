@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

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
                Text("Boosts & Events", style = MaterialTheme.typography.titleMediumEmphasized)
                if (activeModifiers.isNotEmpty() || activeEvents.isNotEmpty()) {
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${activeModifiers.size + activeEvents.size} active", style = MaterialTheme.typography.labelSmall)
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
                                        "${event.timeReductionPercent.toInt()}% speedup · ${Countdown.text(leftSec)} left",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { showEventConfigDialog = event }) {
                                    Text("Configure")
                                }
                            }
                        }
                    }
                }
            }

            if (activeModifiers.isEmpty() && activeEvents.isEmpty()) {
                Text(
                    "No active speed boosts or events. Tap below to track Clock Tower or Potions.",
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
                    Text("Clock Tower")
                }
                FilledTonalButton(onClick = {
                    onUpdateBoosts { it.addBuilderPotion(now) }
                }) {
                    Text("+ Builder Potion")
                }
                FilledTonalButton(onClick = {
                    onUpdateBoosts { it.addClockTowerPotion(now) }
                }) {
                    Text("+ CT Potion")
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
                    "${Countdown.text(leftSec)} left (${mod.multiplier.toInt()}× speed) · saves ~${formatDuration(savedSec)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
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
        title = { Text("Builder Base Clock Tower") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Clock Tower provides 10× speed for your Builder Base builders and Star Laboratory.")
                Text("Select Clock Tower Level: Level $level ($durationMin minutes boost)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.roundToInt() },
                    valueRange = ClockTowerData.MIN_LEVEL.toFloat()..ClockTowerData.MAX_LEVEL.toFloat(),
                    steps = ClockTowerData.MAX_LEVEL - ClockTowerData.MIN_LEVEL - 1,
                )
                Text("Est. time saved: ~${(durationMin * 9) / 60}h ${(durationMin * 9) % 60}m", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onActivate(level) }) {
                Text("Activate Boost")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                    Text("Enable Event")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("Time Reduction: ${reductionPercent.toInt()}%", style = MaterialTheme.typography.labelMedium)
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

