@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.ActiveBoostState
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.EventSettings
import com.killersparrow1.clashtrackers.core.SyncLog
import com.killersparrow1.clashtrackers.core.WidgetModel

private enum class Destination(val label: String, val icon: Int) {
    TIMERS("Timers", R.drawable.ic_timers),
    SETTINGS("Settings", R.drawable.ic_settings),
    SUPPORT("Support", R.drawable.ic_support),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashTrackersContent(
    state: AppState,
    settings: AppSettings,
    model: WidgetModel,
    log: SyncLog,
    now: Long,
    timesCount: Int,
    busy: Boolean,
    onSync: () -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onStateChange: ((AppState) -> AppState) -> Unit,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TIMERS) }
    val screenState = rememberSaveableStateHolder()
    BackHandler(enabled = destination != Destination.TIMERS) { destination = Destination.TIMERS }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    if (destination == Destination.TIMERS) "Clash Trackers" else destination.label,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            })
        },
        bottomBar = {
            ShortNavigationBar(arrangement = ShortNavigationBarArrangement.EqualWeight) {
                Destination.entries.forEach { entry ->
                    ShortNavigationBarItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                screenState.SaveableStateProvider(destination) {
                    when (destination) {
                        Destination.TIMERS -> TimerDashboard(
                            model = model,
                            lastSync = log.lastResult,
                            activeBoostState = state.activeBoosts,
                            eventSettings = state.eventSettings,
                            now = now,
                            busy = busy,
                            onSync = onSync,
                            onUpdateBoosts = { transform ->
                                onStateChange { s -> s.copy(activeBoosts = transform(s.activeBoosts)) }
                            },
                            onUpdateEvents = { transform ->
                                onStateChange { s -> s.copy(eventSettings = transform(s.eventSettings)) }
                            },
                        )
                        Destination.SETTINGS -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item { SettingsSection(settings, state.export, now, onSettingsChange) }
                        }
                        Destination.SUPPORT -> SupportScreen(state, model, log, timesCount, now)
                    }
                }
            }
        }
    }
}

