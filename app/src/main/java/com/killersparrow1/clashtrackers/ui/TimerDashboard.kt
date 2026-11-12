@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.compose.ui.res.stringResource
import com.killersparrow1.clashtrackers.core.formatTimestamp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.AccountMeta
import com.killersparrow1.clashtrackers.core.ActiveBoostState
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.EventSettings
import com.killersparrow1.clashtrackers.core.RowButton
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.SyncLogEntry
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.sync.SyncRunner
import com.killersparrow1.clashtrackers.widget.WidgetIcons

@Composable
fun TimerDashboard(
    model: WidgetModel,
    lastSync: SyncLogEntry?,
    activeBoostState: ActiveBoostState,
    eventSettings: EventSettings,
    accounts: List<AccountMeta>,
    selectedTag: String?,
    now: Long,
    busy: Boolean,
    onSync: () -> Unit,
    onUpdateBoosts: ((ActiveBoostState) -> ActiveBoostState) -> Unit,
    onUpdateEvents: ((EventSettings) -> EventSettings) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    var filter by rememberSaveable { mutableStateOf(TimerFilter.ALL) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val timers = remember(model, filter) { model.timers(filter) }

    // Filters collapse when the list is pushed up and come back when it is pulled down (or at the very top).
    var filtersVisible by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 } }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            private var travelled = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) {
                    // Direction change resets the distance, so a small wobble never toggles the filters.
                    if (travelled != 0f && (travelled > 0f) != (dy > 0f)) travelled = 0f
                    travelled += dy
                    if (travelled < -24f) filtersVisible = false else if (travelled > 24f) filtersVisible = true
                }
                return Offset.Zero
            }
        }
    }
    val showFilters = filtersVisible || atTop

    Column(modifier.fillMaxSize().nestedScroll(scrollConnection)) {
        // The account switcher and the filter chips fade and collapse together; the app title stays put.
        AnimatedVisibility(
            visible = (accounts.isNotEmpty() || model.hasData) && showFilters,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
          Column {
            if (accounts.isNotEmpty()) {
                AccountSwitcherBar(accounts, selectedTag, onSelectAccount, onRenameAccount, onDeleteAccount)
            }
            if (model.hasData) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimerFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(stringResource(entry.labelRes)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
          }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (wide) 2 else 1),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (model.hasData) {
                item(key = "village", span = { GridItemSpan(maxLineSpan) }) { VillageSummary(model) }
            } else {
                item(key = "welcome", span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(stringResource(R.string.welcome_title)) {
                        Text(stringResource(R.string.welcome_sub), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.welcome_step1))
                        Text(stringResource(R.string.welcome_step2))
                        Text(stringResource(R.string.welcome_step3))
                        Text(
                            stringResource(R.string.welcome_privacy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { showHelp = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.help_link)) }
                    }
                }
            }

            item(key = "sync", span = { GridItemSpan(maxLineSpan) }) { SyncCard(lastSync, busy, onSync) }

            item(key = "gold_pass_banner", span = { GridItemSpan(maxLineSpan) }) { GoldPassBanner(selectedTag, now) }

            if (model.hasData) {
                item(key = "stats", span = { GridItemSpan(maxLineSpan) }) { StatTiles(model) }
            }

            if (model.hasData) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (filter == TimerFilter.ALL) stringResource(R.string.village_upgrades) else stringResource(R.string.upgrades_filtered, stringResource(filter.labelRes)),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (timers.isEmpty()) {
                    item(key = "empty_filter", span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                stringResource(R.string.no_timers_in, stringResource(filter.labelRes)),
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
    if (showHelp) HelpDialog(onDismiss = { showHelp = false })
}

/**
 * Round picture of an account's Town Hall at its level, like a profile photo.
 * Uses `1000001_<level>.webp`, which the CI icon step (tools/gen_icons.cjs) already makes for every level;
 * until the level is known it shows the plain Town Hall icon. (Extra files placed in assets/icons by hand
 * are deleted by that step, so no new files are needed.)
 */
@Composable
private fun AccountPicture(townHall: Int?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap = remember(townHall) {
        val px = with(density) { 28.dp.roundToPx() }
        WidgetIcons.get(context).bitmapForLevel(1000001, townHall, px)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(2.dp),
        )
    }
}

@Composable
private fun AccountSwitcherBar(
    accounts: List<AccountMeta>,
    selectedTag: String?,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.tag == selectedTag } ?: accounts.firstOrNull()

    var menuOpen by remember { mutableStateOf(false) }
    // One compact button: tap it for every account, plus Rename / Delete for the current one.
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AssistChip(
            onClick = { menuOpen = true },
            label = { Text("${selectedAccount?.displayName ?: ""}${selectedAccount?.townHall?.let { " \u00B7 TH$it" } ?: ""} \u25BE") },
            leadingIcon = { AccountPicture(selectedAccount?.townHall) },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            accounts.forEach { acct ->
                DropdownMenuItem(
                    leadingIcon = { AccountPicture(acct.townHall) },
                    text = {
                        Column {
                            Text((if (acct.tag == selectedAccount?.tag) "\u2713  " else "") + acct.displayName + (acct.townHall?.let { " \u00B7 TH$it" } ?: ""))
                            Text(acct.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onSelect(acct.tag)
                    },
                )
            }
            if (selectedAccount != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = {
                        menuOpen = false
                        showRenameDialog = true
                    },
                )
                if (accounts.size > 1) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            showDeleteConfirm = true
                        },
                    )
                }
            }
        }
    }

    if (showRenameDialog && selectedAccount != null) {
        PlayerNameDialog(
            current = selectedAccount.name,
            firstLaunch = false,
            onDone = { newName ->
                onRename(selectedAccount.tag, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm && selectedAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_account_title, selectedAccount.tag)) },
            text = { Text(stringResource(R.string.delete_account_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(selectedAccount.tag)
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
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
                    model.townHall?.let { stringResource(R.string.town_hall, it) },
                    stringResource(R.string.builders_busy, model.buildersBusy, model.buildersTotal),
                    if (model.goblinWorking) stringResource(R.string.goblin_working) else null,
                    if (model.showBuilderBase) stringResource(R.string.bb_active) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dayUnit = stringResource(R.string.time_day_short)
            val builderTotal = model.builderRows.filter { it.kind == RowKind.RUNNING }.sumOf { it.remaining }
            val labTotal = (listOf(model.labRow) + model.extraLabRows).filter { it.kind == RowKind.RUNNING }.sumOf { it.remaining }
            if (builderTotal > 0 || labTotal > 0) {
                Text(
                    listOfNotNull(
                        builderTotal.takeIf { it > 0 }?.let { stringResource(R.string.total_builder_time, Countdown.text(it, dayUnit)) },
                        labTotal.takeIf { it > 0 }?.let { stringResource(R.string.total_lab_time, Countdown.text(it, dayUnit)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.nextRunningTimer()?.let { next ->
                next.row.finishAt?.let { finishAt ->
                    Spacer(Modifier.size(4.dp))
                    // Highlighted like the completion chip on the timer cards, so the next finish stands out.
                    Text(
                        stringResource(R.string.next_finish, next.row.name, Countdown.text(next.row.remaining, stringResource(R.string.time_day_short))),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        stringResource(if (failed) R.string.sync_failed else if (last != null) R.string.sync_last else R.string.sync_village),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    last?.let { Text(formatTimestamp(it.at), style = MaterialTheme.typography.bodySmall) }
                }
                if (last != null && !failed) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(stringResource(if (showDetails) R.string.hide_details else R.string.sync_details))
                    }
                }
            }
            if (failed) {
                last?.let { SelectionContainer { Text(SyncRunner.plainText(it.message), style = MaterialTheme.typography.bodyMedium) } }
                Text(stringResource(R.string.sync_retry_hint), style = MaterialTheme.typography.bodyMedium)
            }
            if (last == null) {
                Text(stringResource(R.string.sync_clip_hint), style = MaterialTheme.typography.bodyMedium)
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
                    stringResource(if (busy) R.string.syncing else if (failed) R.string.sync_retry else R.string.sync_from_clipboard),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
            if (last != null && !failed && showDetails) {
                SelectionContainer { Text(SyncRunner.plainText(last.message), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.sync_copy_hint), style = MaterialTheme.typography.bodySmall)
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
                TimerFilter.BUILDER_BASE -> stringResource(R.string.filter_bb)
                TimerFilter.BUILDERS -> stringResource(if (row.goblin) R.string.cat_goblin_builder else R.string.cat_home_builder)
                TimerFilter.LAB -> stringResource(R.string.cat_lab)
                TimerFilter.PETS -> stringResource(R.string.cat_pets)
                else -> stringResource(timer.category.labelRes)
            }
            Text(
                categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val context = LocalContext.current
            val iconPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            val icon = remember(row.iconIds, row.iconLevel, iconPx) {
                val icons = WidgetIcons.get(context)
                row.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, row.iconLevel, iconPx) }?.asImageBitmap()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
                    )
                }
                Text(SyncRunner.plainText(row.label), style = MaterialTheme.typography.titleLargeEmphasized)
            }
            BasicText(
                if (row.kind == RowKind.RUNNING) Countdown.text(row.remaining, stringResource(R.string.time_day_short)) else row.staticStatus,
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
                Text(stringResource(R.string.percent_complete, (row.progress.coerceIn(0f, 1f) * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            }
            if (row.kind == RowKind.RUNNING) {
                row.finishAt?.let {
                    // Highlighted completion time (same finishAt the countdown uses), so it stands out on the card.
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.finishes_at, formatTimestamp(it)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                val helper = when (row.button) {
                    RowButton.APPRENTICE -> stringResource(R.string.apprentice_assigned)
                    RowButton.LAB_ASSISTANT -> stringResource(R.string.assistant_assigned)
                    else -> null
                }
                helper?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            } else {
                Text(
                    stringResource(if (row.kind == RowKind.COMPLETED) R.string.row_completed_hint else R.string.row_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Three quick numbers under the village card: builders busy, when a builder frees up, when the lab is free. */
@Composable
private fun StatTiles(model: WidgetModel) {
    val dayUnit = stringResource(R.string.time_day_short)
    val nextFree = if (model.buildersBusy < model.buildersTotal) stringResource(R.string.stat_free_now)
    else model.builderRows.filter { it.kind == RowKind.RUNNING && !it.goblin }.minOfOrNull { it.remaining }
        ?.let { Countdown.text(it, dayUnit) } ?: "—"
    val labRunning = (listOf(model.labRow) + model.extraLabRows).filter { it.kind == RowKind.RUNNING }
    val nextLab = if (labRunning.isEmpty()) stringResource(R.string.stat_free_now)
    else Countdown.text(labRunning.minOf { it.remaining }, dayUnit)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.stat_builders), "${model.buildersBusy} / ${model.buildersTotal}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatTile(stringResource(R.string.stat_next_free), nextFree, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        StatTile(stringResource(R.string.stat_next_lab), nextLab, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
        }
    }
}

/** A dismissible nudge on Day 4 / Day 8 when a Gold Pass player may have unlocked a bigger builder boost. */
@Composable
private fun GoldPassBanner(tag: String?, now: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    val month = com.killersparrow1.clashtrackers.core.GoldPassLogic.monthKey(now)
    val day = com.killersparrow1.clashtrackers.core.GoldPassLogic.dayOfSeason(now)
    val answer = remember(tag, com.killersparrow1.clashtrackers.sync.GoldPassStore.version) {
        com.killersparrow1.clashtrackers.sync.GoldPassStore.answer(context, tag)
    }
    val dismissed = remember(tag, month, com.killersparrow1.clashtrackers.sync.GoldPassStore.version) {
        com.killersparrow1.clashtrackers.sync.GoldPassStore.dismissedTarget(context, tag, month)
    }
    // Only for this month's answer, and only until the player updates it or dismisses this step.
    val target = if (answer.month == month)
        com.killersparrow1.clashtrackers.core.GoldPassLogic.bannerTarget(day, answer.gold, answer.builder)?.takeIf { it > dismissed }
    else null
    if (target == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.gold_pass_banner, day, target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.gold_pass_banner_update))
                }
                androidx.compose.material3.TextButton(
                    onClick = { com.killersparrow1.clashtrackers.sync.GoldPassStore.dismissBanner(context, tag, month, target) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.gold_pass_banner_dismiss)) }
            }
        }
    }
    if (open) {
        GoldPassDialog(
            initial = answer,
            laterLabel = stringResource(android.R.string.cancel),
            onSave = { gold, b, r ->
                com.killersparrow1.clashtrackers.sync.GoldPassStore.save(context, tag, month, gold, b, r)
                open = false
                scope.launch { com.killersparrow1.clashtrackers.sync.SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            },
            onLater = { open = false },
        )
    }
}
