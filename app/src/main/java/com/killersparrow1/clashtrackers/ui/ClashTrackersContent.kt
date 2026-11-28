@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.pager.PagerState
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    SETTINGS(Destination.SETTINGS, R.string.nav_settings),
    PERSONALIZATION(Destination.SETTINGS, R.string.settings_tab_personalization),
    SUPPORT(Destination.SETTINGS, R.string.nav_support),
}

/** Height of the Plan and Settings sub-tab row (text-only tabs). */
private val SubTabsHeight = 48.dp

/**
 * Slides to [target]. A neighbouring page slides on [spec]. A far page does not sweep past (or flash through) the pages in
 * between: the pager fades out, jumps, and fades back in.
 */
private suspend fun PagerState.goTo(target: Int, spec: AnimationSpec<Float>, fade: Animatable<Float, AnimationVector1D>) {
    val from = currentPage
    if (target == from) return
    if (kotlin.math.abs(target - from) > 1) {
        fade.animateTo(0f, tween(90))
        scrollToPage(target)
        fade.animateTo(1f, tween(160))
    } else {
        animateScrollToPage(target, animationSpec = spec)
    }
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
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    var setupOpen by rememberSaveable { mutableStateOf(!QuickSetupPrefs.done(setupContext) && !model.hasData) }
    // Quick setup already asked about these, so the older reminder dialog waits until the next launch.
    val setupShownThisLaunch = rememberSaveable { setupOpen }
    PermissionPrompt(enabled = !setupOpen && !setupShownThisLaunch)
    if (setupOpen) QuickSetup(onDone = { setupOpen = false })
    EventsAutoRefresh()
    GameDataAutoRefresh()
    var developerOpen by rememberSaveable { mutableStateOf(false) }
    var communityTest by remember { mutableStateOf(false) }
    CommunityPrompt(
        blocked = setupOpen || developerOpen || ScrollBlurAdvice.suggest,
        test = communityTest,
        onTestDone = { communityTest = false },
    )
    val tapScope = androidx.compose.runtime.rememberCoroutineScope()
    // One pager holds every page, so swiping follows the finger and tabs and sub-tabs share the same motion.
    val pager = androidx.compose.foundation.pager.rememberPagerState { Page.entries.size }
    val pagerFade = remember { Animatable(1f) }
    val pagerBlur = rememberScrollBlur(pager)
    if (ScrollBlurAdvice.suggest) {
        val adviceContext = LocalContext.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ScrollBlurAdvice.answered(adviceContext) },
            title = { Text(stringResource(R.string.scroll_blur_advice_title)) },
            text = { Text(stringResource(R.string.scroll_blur_advice_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ScrollBlurSetting.set(adviceContext, ScrollBlurSetting.OFF)
                    ScrollBlurAdvice.answered(adviceContext)
                }) { Text(stringResource(R.string.scroll_blur_advice_off)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ScrollBlurAdvice.answered(adviceContext) }) {
                    Text(stringResource(R.string.scroll_blur_advice_keep))
                }
            },
        )
    }
    // The clock ticks every second and rebuilds the model; each tick would recompose every page that is on screen or next to it.
    // While a swipe or a slide is running the pages keep the last model and time, and catch up the moment it stops.
    val tabsMoving = pager.isScrollInProgress
    var pageModel by remember { mutableStateOf(model) }
    var pageNow by remember { mutableStateOf(now) }
    LaunchedEffect(model, now, tabsMoving) {
        if (!tabsMoving) {
            pageModel = model
            pageNow = now
        }
    }
    val destination = Page.entries[pager.currentPage].dest
    val reduced = rememberReducedMotion()
    // Tabs move on a spring (a short tween when the phone has animations removed).
    val pageSpring = remember(reduced) {
        if (reduced) tween<Float>(180) else spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    }
    val barState = rememberFloatingBarState(pager)
    // The sub-tabs (Plan, Settings) hide and show together with the bottom bar.
    val subHide = remember { Animatable(0f) }
    LaunchedEffect(barState.subVisible, reduced) {
        subHide.animateTo(if (barState.subVisible || reduced) 0f else 1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
    }
    val floatingOn = settings.floatingTabBar
    // Switching to the docked bar brings everything back.
    LaunchedEffect(floatingOn) { if (!floatingOn) barState.show() }
    val barItems = remember { Destination.entries.map { TabBarItem(it.labelRes, it.icon) } }
    val supportTaps = remember { mutableListOf<Long>() }
    // Tap the Settings tab 11 times within 4 seconds to open the hidden developer options.
    fun navigate(entry: Destination) {
        barState.show()
        if (entry != destination) tapScope.launch { pager.goTo(entry.firstPage(), pageSpring, pagerFade) }
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
    BackHandler(enabled = pager.currentPage != 0) { tapScope.launch { pager.goTo(0, pageSpring, pagerFade) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val floating = floatingOn && !wide
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    // The title slides the way the tabs moved; it changes when the page passes the middle of a swipe.
                    SlideSwitcher(index = destination.ordinal, reduced = reduced) { i ->
                        val shown = Destination.entries[i]
                        Text(
                            if (shown == Destination.TIMERS) stringResource(R.string.app_name) else stringResource(shown.labelRes),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            modifier = Modifier.titleEasterEgg(),
                        )
                    }
                }, actions = {
                    // A fixed-size slot, so the bar never re-lays out when the button comes and goes.
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        SlideSwitcher(index = destination.ordinal, reduced = reduced) { i ->
                            if (Destination.entries[i] == Destination.TIMERS) {
                                val openContext = androidx.compose.ui.platform.LocalContext.current
                                val failed = stringResource(R.string.open_game_failed)
                                OpenGameButton(onClick = {
                                    if (!openGameLink(openContext, CLASH_MORE_SETTINGS_LINK)) {
                                        tapScope.launch { snackbarHost.showSnackbar(failed) }
                                    }
                                })
                            }
                        }
                    }
                })
            },
            bottomBar = {
                if (!wide && !floatingOn) {
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
            snackbarHost = {
                Box(Modifier.padding(bottom = if (floating) FloatingBar.ContentInset - 4.dp else 0.dp)) { SnackbarHost(snackbarHost) }
            },
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
                    CompositionLocalProvider(LocalBarInset provides (if (floating) FloatingBar.ContentInset else 0.dp)) {
                    Column(
                        Modifier.widthIn(max = 840.dp).fillMaxSize()
                            .then(if (!floating || reduced) Modifier else Modifier.nestedScroll(barState.connection)),
                    ) {
                        val siblings = Page.entries.filter { it.dest == destination }
                        // The pager always fills the space and never changes size. The sub-tab row floats over its top edge, and the
                        // pages that have one leave room for it in their own padding, so nothing re-lays out while tabs change.
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pagerFade.value }.scrollBlur(pagerBlur),
                            // The page you are on plus one each side stay composed, so a swipe or a skip never shows a blank page.
                            beyondViewportPageCount = 1,
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = pager,
                                // Half a page is the default; a quarter makes a shorter swipe (or a lighter flick) change the tab.
                                snapPositionalThreshold = 0.25f,
                                snapAnimationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                            ),
                        ) { index ->
                            val pageHasTabs = Page.entries.count { it.dest == Page.entries[index].dest } > 1
                            Box(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalTopInset provides (if (pageHasTabs) SubTabsHeight else 0.dp)) {
                            when (Page.entries[index]) {
                                Page.TIMERS -> TimerDashboard(
                                    model = pageModel,
                                    lastSync = log.lastResult,
                                    activeBoostState = state.activeBoosts,
                                    eventSettings = state.eventSettings,
                                    accounts = accounts,
                                    selectedTag = selectedTag,
                                    now = pageNow,
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
                                Page.PLAN_ROAD -> PlanPage(state.export, pageModel, pageNow, 0)
                                Page.PLAN_BEDTIME -> PlanPage(state.export, pageModel, pageNow, 1)
                                Page.CLASH_EVENTS -> EventsPage(pageNow)
                                Page.SETTINGS -> SettingsPage(0, settings, state.export, pageNow, onSettingsChange, selectedTag, state, pageModel, log, timesCount)
                                Page.PERSONALIZATION -> SettingsPage(1, settings, state.export, pageNow, onSettingsChange, selectedTag, state, pageModel, log, timesCount)
                                Page.SUPPORT -> SettingsPage(2, settings, state.export, pageNow, onSettingsChange, selectedTag, state, pageModel, log, timesCount)
                            }
                            }
                            }
                        }
                        val rowAlpha by androidx.compose.animation.core.animateFloatAsState(
                            if (siblings.size > 1) 1f else 0f, tween(if (reduced) 0 else 160), label = "subTabsAlpha",
                        )
                        // While the row fades out after leaving Plan or Settings it keeps the tabs it had (Timers and Clash have none).
                        val rowTabs = remember { arrayOf(siblings) }
                        if (siblings.size > 1) rowTabs[0] = siblings
                        val tabs = rowTabs[0]
                        if (tabs.size > 1 && (siblings.size > 1 || rowAlpha > 0f)) {
                            val first = Page.entries.indexOf(tabs.first())
                            androidx.compose.material3.PrimaryTabRow(
                                selectedTabIndex = (pager.currentPage - first).coerceIn(0, tabs.size - 1),
                                // Transform and opacity only: the row slides up under the app bar, the layout never changes.
                                modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                                    translationY = -SubTabsHeight.toPx() * subHide.value
                                    alpha = (1f - subHide.value) * rowAlpha
                                },
                            ) {
                                tabs.forEachIndexed { i, p ->
                                    androidx.compose.material3.Tab(
                                        selected = pager.currentPage == first + i,
                                        onClick = { tapScope.launch { pager.goTo(first + i, pageSpring, pagerFade) } },
                                        text = { p.labelRes?.let { Text(stringResource(it)) } },
                                    )
                                }
                            }
                        }
                        }
                    }
                    }
                }
            }
        }
        if (floating) {
            FloatingTabBar(
                items = barItems,
                selectedIndex = destination.ordinal,
                onSelect = { navigate(Destination.entries[it]) },
                barState = barState,
                reduced = reduced,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (developerOpen) DeveloperOptionsScreen(selectedTag, log, onClose = { developerOpen = false }, onTestCommunity = { communityTest = true })
    }
}
