@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.AccountMeta
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.SyncLog
import com.killersparrow1.clashtrackers.core.WidgetModel

private enum class Destination(@StringRes val labelRes: Int, val icon: Int) {
    TIMERS(R.string.nav_timers, R.drawable.ic_timers),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
    SUPPORT(R.string.nav_support, R.drawable.ic_support),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashTrackersContent(
    state: AppState,
    settings: AppSettings,
    model: WidgetModel,
    log: SyncLog,
    accounts: List<AccountMeta>,
    selectedTag: String?,
    now: Long,
    timesCount: Int,
    busy: Boolean,
    onSync: () -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onStateChange: ((AppState) -> AppState) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TIMERS) }
    var developerOpen by rememberSaveable { mutableStateOf(false) }
    val tapScope = androidx.compose.runtime.rememberCoroutineScope()
    val supportTaps = remember { mutableListOf<Long>() }
    // Tap the Support tab 11 times within 4 seconds to open the hidden developer options.
    fun navigate(entry: Destination) {
        destination = entry
        if (entry != Destination.SUPPORT || developerOpen) return
        val t = System.currentTimeMillis()
        supportTaps.add(t)
        supportTaps.removeAll { t - it > 4_000L }
        val left = 11 - supportTaps.size
        when {
            left <= 0 -> {
                supportTaps.clear()
                developerOpen = true
                tapScope.launch { snackbarHost.currentSnackbarData?.dismiss(); snackbarHost.showSnackbar("Developer options enabled") }
            }
            left <= 4 -> tapScope.launch {
                snackbarHost.currentSnackbarData?.dismiss()
                snackbarHost.showSnackbar(if (left == 1) "1 more tap to enable developer options" else "$left more taps to enable developer options")
            }
        }
    }
    val screenState = rememberSaveableStateHolder()
    BackHandler(enabled = destination != Destination.TIMERS) { destination = Destination.TIMERS }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(
                        if (destination == Destination.TIMERS) stringResource(R.string.app_name) else stringResource(destination.labelRes),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.titleEasterEgg(),
                    )
                }, actions = {
                    if (destination == Destination.TIMERS) {
                        val openContext = androidx.compose.ui.platform.LocalContext.current
                        val failed = stringResource(R.string.open_game_failed)
                        OpenGameButton(onClick = {
                            if (!openGameLink(openContext, CLASH_MORE_SETTINGS_LINK)) {
                                tapScope.launch { snackbarHost.showSnackbar(failed) }
                            }
                        })
                    }
                })
            },
            bottomBar = {
                if (!wide) {
                    ShortNavigationBar(arrangement = ShortNavigationBarArrangement.EqualWeight) {
                        Destination.entries.forEach { entry ->
                            ShortNavigationBarItem(
                                selected = destination == entry,
                                onClick = { navigate(entry) },
                                icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                                label = { Text(stringResource(entry.labelRes)) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        Destination.entries.forEach { entry ->
                            NavigationRailItem(
                                selected = destination == entry,
                                onClick = { navigate(entry) },
                                icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                                label = { Text(stringResource(entry.labelRes)) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        screenState.SaveableStateProvider(destination) {
                            when (destination) {
                                Destination.TIMERS -> TimerDashboard(
                                    model = model,
                                    lastSync = log.lastResult,
                                    activeBoostState = state.activeBoosts,
                                    eventSettings = state.eventSettings,
                                    accounts = accounts,
                                    selectedTag = selectedTag,
                                    now = now,
                                    busy = busy,
                                    export = state.export,
                                    onSync = onSync,
                                    onUpdateBoosts = { transform ->
                                        onStateChange { s -> s.copy(activeBoosts = transform(s.activeBoosts)) }
                                    },
                                    onUpdateEvents = { transform ->
                                        onStateChange { s -> s.copy(eventSettings = transform(s.eventSettings)) }
                                    },
                                    onSelectAccount = onSelectAccount,
                                    onRenameAccount = onRenameAccount,
                                    onDeleteAccount = onDeleteAccount,
                                    wide = wide,
                                )
                                Destination.SETTINGS -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    item { SettingsSection(settings, state.export, now, onSettingsChange, selectedTag) }
                                }
                                Destination.SUPPORT -> SupportScreen(state, model, log, timesCount, now)
                            }
                        }
                    }
                }
            }
        }
        if (developerOpen) DeveloperOptionsScreen(selectedTag, log, onClose = { developerOpen = false })
    }
}
