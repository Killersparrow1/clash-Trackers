package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.Bedtime
import com.killersparrow1.clashtrackers.core.GameEvent
import com.killersparrow1.clashtrackers.core.GameEvents
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.update.DataUpdater
import com.killersparrow1.clashtrackers.update.ClashKingDatesStore
import com.killersparrow1.clashtrackers.update.EventsStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Clash > Events: countdowns to the game's regular events, computed from their usual schedule (no internet). */
@Composable
internal fun EventsPage(now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    EventsStore.load(context)
    ClashKingDatesStore.load(context)
    val config = EventsStore.config
    val dates = ClashKingDatesStore.dates
    val events = remember(now / 30, config, dates) { GameEvents.upcoming(now, config, dates) }
    // The latest successful sync of either source (the schedule file or ClashKing's dates).
    val syncedAt = maxOf(EventsStore.updatedAt, ClashKingDatesStore.updatedAt)
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(EventsStore.auto(context)) }
    val zone = remember { ZoneId.systemDefault() }
    val format = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    fun local(epoch: Long) = format.format(Instant.ofEpochSecond(epoch).atZone(zone))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { e ->
            val name = e.name ?: stringResource(eventName(e.id))
            val left = Bedtime.durationLabel((e.target - now).coerceAtLeast(0))
            Card(
                Modifier.fillMaxWidth().wiggleOnTap(),
                colors = CardDefaults.cardColors(
                    containerColor = if (e.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            e.point -> stringResource(R.string.clash_ev_in, left)
                            e.active -> stringResource(R.string.clash_ev_ends_in, left)
                            else -> stringResource(R.string.clash_ev_starts_in, left)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        if (e.point) local(e.endsAt) else stringResource(R.string.clash_ev_range, local(e.startsAt), local(e.endsAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.clash_ev_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (syncedAt > 0) stringResource(R.string.clash_ev_updated, local(syncedAt / 1000))
                    else stringResource(R.string.clash_ev_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                refreshError?.let { Text(stringResource(R.string.update_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.clash_ev_auto), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(checked = auto, onCheckedChange = {
                        auto = it
                        EventsStore.setAuto(context, it)
                    })
                }
                var alerts by remember { mutableStateOf(com.killersparrow1.clashtrackers.notify.EventAlerts.enabled(context)) }
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.ev_settings_enable), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(checked = alerts, onCheckedChange = {
                        alerts = it
                        com.killersparrow1.clashtrackers.notify.EventAlerts.setEnabled(context, it)
                    })
                }
                Text(
                    stringResource(R.string.ev_settings_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = !refreshing,
                    onClick = {
                        refreshing = true
                        refreshError = null
                        scope.launch {
                            // Both sources are asked; it is only an error when neither could be refreshed.
                            val schedule = runCatching { EventsStore.refresh(context) }
                            val clashKing = runCatching { ClashKingDatesStore.refresh(context) }
                            if (schedule.isFailure && clashKing.isFailure) {
                                val e = clashKing.exceptionOrNull() ?: schedule.exceptionOrNull()
                                refreshError = e?.message ?: e?.javaClass?.simpleName
                            }
                            com.killersparrow1.clashtrackers.notify.EventAlerts.schedule(context)
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(if (refreshing) R.string.update_checking else R.string.clash_ev_refresh)) }
            }
        }
    }
}

/** Keeps the event schedule fresh: refreshes when the app opens (at most every 12 hours, if switched on in Clash > Events). */
@Composable
internal fun EventsAutoRefresh() {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        EventsStore.refreshIfDue(context)
        ClashKingDatesStore.refreshIfDue(context)
        com.killersparrow1.clashtrackers.notify.EventAlerts.schedule(context) // new dates can move an alert
    }
}

internal fun eventName(id: String): Int = when (id) {
    GameEvents.CLAN_GAMES -> R.string.clash_ev_clan_games
    GameEvents.RAID_WEEKEND -> R.string.clash_ev_raid
    GameEvents.CWL_SIGNUP -> R.string.clash_ev_cwl_signup
    GameEvents.CWL_WARS -> R.string.clash_ev_cwl_wars
    GameEvents.SEASON_END -> R.string.clash_ev_season_end
    else -> R.string.clash_ev_league_reset
}

private sealed interface DataCheck {
    data object Idle : DataCheck
    data object Checking : DataCheck
    data object Same : DataCheck
    data class Newer(val text: String, val townHall: Int, val items: Int) : DataCheck
    data class Failed(val message: String) : DataCheck
}

/** Settings: which game data the app uses, whether it updates by itself, and a manual check. */
@Composable
internal fun GameDataCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = MaxDataStore.version // read so this page redraws after an update
    val data = remember(version) { MaxDataStore.get(context) }
    val downloaded = remember(version) { MaxDataStore.isDownloaded(context) }
    var check by remember { mutableStateOf<DataCheck>(DataCheck.Idle) }

    var auto by remember { mutableStateOf(DataUpdater.auto(context)) }
    run {
        run {
            SectionCard(stringResource(R.string.clash_data_title)) {
                Text(stringResource(R.string.clash_data_desc), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(
                        if (downloaded) R.string.clash_data_current_downloaded else R.string.clash_data_current_builtin,
                        data.maxTownHall, data.items.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                when (val c = check) {
                    is DataCheck.Newer -> {
                        Text(stringResource(R.string.clash_data_newer, c.townHall, c.items), style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = {
                                check = if (MaxDataStore.install(context, c.text)) DataCheck.Idle
                                else DataCheck.Failed(context.getString(R.string.clash_data_install_failed))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.clash_data_install)) }
                    }
                    DataCheck.Same -> Text(stringResource(R.string.clash_data_same), style = MaterialTheme.typography.bodyMedium)
                    DataCheck.Checking -> Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
                    is DataCheck.Failed -> Text(
                        stringResource(R.string.update_failed, c.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    DataCheck.Idle -> Unit
                }
                OutlinedButton(
                    onClick = {
                        check = DataCheck.Checking
                        scope.launch {
                            check = try {
                                val r = DataUpdater.fetch(context)!!
                                // Never go back to fewer Town Halls than the app already knows.
                                if (!DataUpdater.isBetter(r, data, DataUpdater.currentText(context))) DataCheck.Same
                                else DataCheck.Newer(r.text, r.data.maxTownHall, r.data.items.size)
                            } catch (e: Exception) {
                                DataCheck.Failed(e.message ?: e.javaClass.simpleName)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.clash_data_check)) }
                if (downloaded) {
                    OutlinedButton(
                        onClick = { MaxDataStore.useBundled(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.clash_data_use_builtin)) }
                }
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.clash_data_auto), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(checked = auto, onCheckedChange = {
                        auto = it
                        DataUpdater.setAuto(context, it)
                    })
                }
                Text(
                    stringResource(R.string.clash_data_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Keeps the game data fresh: fetched quietly when the app opens (at most hourly, if switched on in Settings), then icons for any new items. */
@Composable
internal fun GameDataAutoRefresh() {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        DataUpdater.refreshIfDue(context)
        // Pictures come from assets.clashk.ing (missing ones now, all of them again about weekly).
        runCatching { com.killersparrow1.clashtrackers.update.RemoteIcons.sync(context) }
    }
}
