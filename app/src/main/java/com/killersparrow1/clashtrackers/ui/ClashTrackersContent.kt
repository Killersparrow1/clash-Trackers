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
    PLAN(R.string.nav_plan, R.drawable.ic_plan),
    CLASH(R.string.nav_clash, R.drawable.ic_clash),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

/** Every swipeable page, in order. The first page of a tab is what its bottom-bar item opens. */
private enum class Page(val dest: Destination, @StringRes val labelRes: Int?) {
    TIMERS(Destination.TIMERS, null),
    PLAN_ROAD(Destination.PLAN, R.string.plan_tab_road),
    PLAN_BEDTIME(Destination.PLAN, R.string.plan_tab_bedtime),
    CLASH_EVENTS(Destination.CLASH, R.string.clash_tab_events),
    CLASH_DATA(Destination.CLASH, R.string.clash_tab_data),
    SETTINGS(Destination.SETTINGS, R.string.nav_settings),
    PERSONALIZATION(Destination.SETTINGS, R.string.settings_tab_personalization),
    SUPPORT(Destination.SETTINGS, R.string.nav_support),
}

private fun Destination.firstPage(): Int = Page.entries.indexOfFirst { it.dest == this }

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
    UpdatePrompt()
    var developerOpen by rememberSaveable { mutableStateOf(false) }
    val tapScope = androidx.compose.runtime.rememberCoroutineScope()
    // One pager holds every page, so swiping follows the finger and tabs and sub-tabs share the same motion.
    val pager = androidx.compose.foundation.pager.rememberPagerState { Page.entries.size }
    val destination = Page.entries[pager.currentPage].dest
    val supportTaps = remember { mutableListOf<Long>() }
    // Tap the Settings tab 11 times within 4 seconds to open the hidden developer options.
    fun navigate(entry: Destination) {
        if (entry != destination) tapScope.launch { pager.animateScrollToPage(entry.firstPage()) }
        if (entry != Destination.SETTINGS || developerOpen) return
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
    BackHandler(enabled = pager.currentPage != 0) { tapScope.launch { pager.animateScrollToPage(0) } }

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
                    Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        val siblings = Page.entries.filter { it.dest == destination }
                        if (siblings.size > 1) {
                            val first = destination.firstPage()
                            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = pager.currentPage - first) {
                                siblings.forEachIndexed { i, p ->
                                    androidx.compose.material3.Tab(
                                        selected = pager.currentPage == first + i,
                                        onClick = { tapScope.launch { pager.animateScrollToPage(first + i) } },
                                        text = { Text(stringResource(p.labelRes!!)) },
                                    )
                                }
                            }
                        }
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pager,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            beyondViewportPageCount = 1,
                        ) { index ->
                            when (Page.entries[index]) {
                                Page.TIMERS -> TimerDashboard(
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
                                Page.PLAN_ROAD -> PlanPage(state.export, model, now, 0)
                                Page.PLAN_BEDTIME -> PlanPage(state.export, model, now, 1)
                                Page.CLASH_EVENTS -> EventsPage(now)
                                Page.CLASH_DATA -> DataPage()
                                Page.SETTINGS -> SettingsPage(0, settings, state.export, now, onSettingsChange, selectedTag, state, model, log, timesCount)
                                Page.PERSONALIZATION -> SettingsPage(1, settings, state.export, now, onSettingsChange, selectedTag, state, model, log, timesCount)
                                Page.SUPPORT -> SettingsPage(2, settings, state.export, now, onSettingsChange, selectedTag, state, model, log, timesCount)
                            }
                        }
                    }
                }
            }
        }
        if (developerOpen) DeveloperOptionsScreen(selectedTag, log, onClose = { developerOpen = false })
    }
}
