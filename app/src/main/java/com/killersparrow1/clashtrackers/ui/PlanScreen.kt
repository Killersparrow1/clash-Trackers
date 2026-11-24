@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.killersparrow1.clashtrackers.core.PlanFilter
import com.killersparrow1.clashtrackers.core.PlanSort
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.Bedtime
import com.killersparrow1.clashtrackers.core.PlanCandidate
import com.killersparrow1.clashtrackers.core.PlanSlot
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.BobPlanner
import com.killersparrow1.clashtrackers.core.SleepWindow
import com.killersparrow1.clashtrackers.core.VillageExport
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.sync.SleepStore
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One page of the Plan tab: 0 is Road to max, 1 is Bedtime (what to start in each free slot before you sleep). */
@Composable
internal fun PlanPage(export: VillageExport?, model: WidgetModel, now: Long, page: Int) {
    val context = LocalContext.current
    val data = remember(MaxDataStore.version) { MaxDataStore.get(context) }
    var window by remember { mutableStateOf(SleepStore.get(context)) }
    val zone = remember { ZoneId.systemDefault() }

    var filter by remember { mutableStateOf(PlanFilter()) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (export == null || !model.hasData) {
                item { Text(stringResource(R.string.welcome_sub), style = MaterialTheme.typography.bodyLarge) }
                return@LazyColumn
            }
            if (page == 0) {
                item { RoadToMaxCard(export, model.buildersTotal) }
                return@LazyColumn
            }
            // The 6th builder plan sits first so it is in view when Bedtime level up opens.
            item(key = "bob") {
                val bob = remember(export, data) { BobPlanner.compute(data, export) }
                if (bob != null) BobCard(bob)
            }
            item {
                SleepCard(window) {
                    window = it
                    SleepStore.set(context, it)
                }
            }
            item { FilterCard(filter) { filter = it } }
            for (slot in PlanSlot.entries) {
                // The Builder Base cards need a Builder Hall in the village.
                if ((slot == PlanSlot.BB_BUILDER || slot == PlanSlot.STAR_LAB) && Bedtime.builderHall(export) == null) continue
                item(key = "plan_$slot") {
                    val busyRows = model.builderRows.filter { it.kind == RowKind.RUNNING && it.finishAt != null }
                    val freeAt: Long = when (slot) {
                        PlanSlot.BUILDER ->
                            if (model.buildersBusy < model.buildersTotal) now else busyRows.minOfOrNull { it.finishAt!! } ?: now
                        PlanSlot.LAB -> model.labRow.takeIf { it.kind == RowKind.RUNNING }?.finishAt ?: now
                        PlanSlot.PET -> model.petRow.takeIf { it.kind == RowKind.RUNNING }?.finishAt ?: now
                        // Only running upgrades are known, so two Builder Base builders are assumed.
                        PlanSlot.BB_BUILDER -> {
                            val running = model.bbRows.filter { it.kind == RowKind.RUNNING && it.finishAt != null }
                            if (running.size < 2) now else running.minOf { it.finishAt!! }
                        }
                        PlanSlot.STAR_LAB -> model.starLabRow?.takeIf { it.kind == RowKind.RUNNING }?.finishAt ?: now
                    }
                    // A slot that is free now keeps moving with the clock: recompute once a minute, not every second.
                    val key = if (freeAt <= now) now / 60 * 60 else freeAt
                    val plan = remember(export, slot, key, window, filter) { Bedtime.plan(data, export, slot, key, window, zone, filter = filter) }
                    SlotCard(slot, freeAt - now > 60, freeAt, plan?.items.orEmpty(), zone)
                }
            }
            item {
                Text(
                    stringResource(R.string.plan_estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
}

@Composable
private fun FilterCard(filter: PlanFilter, onChange: (PlanFilter) -> Unit) {
    fun <T> Set<T>.toggle(x: T) = if (x in this) this - x else this + x
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow {
                for ((c, label) in listOf('d' to R.string.plan_filter_defence, 'p' to R.string.plan_filter_permanent, 'a' to R.string.plan_filter_army, 'r' to R.string.plan_filter_resource)) {
                    FilterChip(c in filter.categories, { onChange(filter.copy(categories = filter.categories.toggle(c))) }, { Text(stringResource(label)) })
                }
                FilterChip(filter.onlyNew, { onChange(filter.copy(onlyNew = !filter.onlyNew)) }, { Text(stringResource(R.string.plan_filter_new)) })
            }
            ChipRow {
                for ((r, label) in listOf(0 to R.string.plan_filter_gold, 1 to R.string.plan_filter_elixir, 2 to R.string.plan_filter_dark)) {
                    FilterChip(r in filter.resources, { onChange(filter.copy(resources = filter.resources.toggle(r))) }, { Text(stringResource(label)) })
                }
            }
            ChipRow {
                for ((sort, label) in listOf(PlanSort.FINISH to R.string.plan_sort_time, PlanSort.COST_HIGH to R.string.plan_sort_cost_high, PlanSort.COST_LOW to R.string.plan_sort_cost_low)) {
                    FilterChip(filter.sort == sort, { onChange(filter.copy(sort = sort)) }, { Text(stringResource(label)) })
                }
            }
            if (filter != PlanFilter()) {
                TextButton(onClick = { onChange(PlanFilter()) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.plan_filter_clear))
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private fun timeText(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun SleepCard(window: SleepWindow, onChange: (SleepWindow) -> Unit) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.plan_bedtime), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.plan_sleep_from), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeChip(timeText(window.startMin)) { editing = true }
                Text(stringResource(R.string.plan_sleep_to), style = MaterialTheme.typography.bodyLarge)
                TimeChip(timeText(window.endMin)) { editing = false }
            }
            Text(stringResource(R.string.plan_bedtime_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    editing?.let { isStart ->
        TimeDialog(
            initialMin = if (isStart) window.startMin else window.endMin,
            onDone = { minutes ->
                onChange(if (isStart) window.copy(startMin = minutes) else window.copy(endMin = minutes))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun TimeDialog(initialMin: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialMin / 60, initialMin % 60, DateFormat.is24HourFormat(LocalContext.current))
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.plan_pick_time), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                TimePicker(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onDone(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

@Composable
private fun SlotCard(slot: PlanSlot, waiting: Boolean, freeAt: Long, items: List<PlanCandidate>, zone: ZoneId) {
    val locale = Locale.getDefault()
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEjmm") }
    val format = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    fun clock(epoch: Long) = format.format(Instant.ofEpochSecond(epoch).atZone(zone))
    val title = stringResource(
        when (slot) {
            PlanSlot.BUILDER -> R.string.plan_builders
            PlanSlot.LAB -> R.string.plan_lab
            PlanSlot.PET -> R.string.plan_pets
            PlanSlot.BB_BUILDER -> R.string.plan_bb_builders
            PlanSlot.STAR_LAB -> R.string.plan_star_lab
        },
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (waiting) stringResource(R.string.plan_next_free, clock(freeAt)) else stringResource(R.string.plan_free_now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.plan_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            val names = Names.get(LocalContext.current)
            val dayUnit = stringResource(R.string.time_day_short)
            items.forEach { item ->
                CandidateRow(names.name(item.id), item, stringResource(R.string.plan_done_at, clock(item.finishAt)), dayUnit)
            }
        }
    }
}

@Composable
private fun CandidateRow(name: String, item: PlanCandidate, doneText: String, dayUnit: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val icon = remember(item.id) {
        WidgetIcons.get(context).bitmapForLevel(item.id, null, with(density) { 44.dp.roundToPx() })?.asImageBitmap()
    }
    Column {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.iconTile(44.dp))
            } else {
                Spacer(Modifier.size(44.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$name ➛ ${item.level}" + if (item.count > 1) " ×${item.count}" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Bedtime.durationLabel(item.seconds, dayUnit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(doneText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                if (item.cost > 0) CostChip(item.resource, item.cost)
            }
        }
    }
}

@DrawableRes
private fun resourceIcon(resource: Int): Int = when (resource) {
    1 -> R.drawable.road_elixir
    2 -> R.drawable.road_dark_elixir
    else -> R.drawable.road_gold
}

@Composable
private fun CostChip(resource: Int, cost: Long) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painterResource(resourceIcon(resource)), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(compact(cost), style = MaterialTheme.typography.labelLarge)
    }
}
