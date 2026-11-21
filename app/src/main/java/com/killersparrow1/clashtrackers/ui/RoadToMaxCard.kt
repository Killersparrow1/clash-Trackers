@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.killersparrow1.clashtrackers.core.RoadItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.RoadToMaxCalc
import com.killersparrow1.clashtrackers.core.RoadTotals
import com.killersparrow1.clashtrackers.core.VillageExport
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import java.util.Locale

/**
 * "Road to max": time and resources still needed to max out the current Town Hall, from the village export and the
 * bundled game data, as a summary, the time and resources left, and one card per group (buildings, walls, heroes,
 * laboratory, pets) with how much of it is done. Estimates: base times and costs, no potions, helpers, events or
 * Gold Pass.
 */
@Composable
internal fun RoadToMaxCard(export: VillageExport?, builders: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data = remember(MaxDataStore.version) { MaxDataStore.get(context) }
    val road = remember(export, data) { export?.let { RoadToMaxCalc.compute(data, it) } } ?: return
    val dayUnit = stringResource(R.string.time_day_short)
    val n = builders.coerceAtLeast(1)
    val timed = road.builderWork + road.lab + road.pets

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CollapsibleCard(stringResource(R.string.road_title, road.townHall), "road_summary", alwaysOpen = true) {
            ProgressLine(road.overallDone, large = true)
        }

        CollapsibleCard(stringResource(R.string.road_time_left), "road_time_left", alwaysOpen = true) {
            RoadRow(
                label = stringResource(R.string.road_builders_x, n),
                value = formatDuration(road.builderSeconds(n), dayUnit),
                detail = if (n > 1 && road.builderWork.seconds > 0)
                    stringResource(R.string.road_builders_work, n, formatDuration(road.builderWork.seconds, dayUnit))
                else null,
                buildingId = 1000015,
            )
            RoadRow(stringResource(R.string.road_lab), formatDuration(road.lab.seconds, dayUnit), buildingId = 1000007)
            RoadRow(stringResource(R.string.road_pets), formatDuration(road.pets.seconds, dayUnit), buildingId = 1000068)
        }

        CollapsibleCard(stringResource(R.string.road_resources_left), "road_resources_left", alwaysOpen = true) {
            RoadRow(stringResource(R.string.resource_gold), compact(timed.gold), iconRes = R.drawable.road_gold)
            RoadRow(stringResource(R.string.resource_elixir), compact(timed.elixir), iconRes = R.drawable.road_elixir)
            RoadRow(stringResource(R.string.resource_dark_elixir), compact(timed.darkElixir), iconRes = R.drawable.road_dark_elixir)
            if (road.walls.goldOrElixir > 0) {
                RoadRow(stringResource(R.string.road_gold_or_elixir), compact(road.walls.goldOrElixir), iconRes = R.drawable.road_gold)
            }
        }

        fun items(group: Char) = road.items.filter { it.group == group }
        CategoryCard(
            title = stringResource(R.string.road_buildings), buildingId = 1000015,
            done = road.levels.buildings.done,
            seconds = road.buildings.seconds / n, remaining = road.buildings, dayUnit = dayUnit, items = items('b'),
        )
        CategoryCard(
            title = stringResource(R.string.road_defences), buildingId = 1000008,
            done = road.levels.defences.done,
            seconds = road.defences.seconds / n, remaining = road.defences, dayUnit = dayUnit, items = items('d'),
        )
        if (road.items.any { it.group == 'c' }) CategoryCard(
            title = stringResource(R.string.road_crafted), buildingId = 1000097,
            done = road.levels.crafted.done,
            seconds = road.crafted.seconds / n, remaining = road.crafted, dayUnit = dayUnit, items = items('c'),
        )
        CategoryCard(
            title = stringResource(R.string.road_walls), buildingId = 1000010,
            done = road.levels.walls.done,
            seconds = null, remaining = road.walls, dayUnit = dayUnit, items = items('w'),
        )
        CategoryCard(
            title = stringResource(R.string.road_heroes), buildingId = 1000071,
            done = road.levels.heroes.done,
            seconds = road.heroSeconds(n), remaining = road.heroes, dayUnit = dayUnit, items = items('h'),
        )
        CategoryCard(
            title = stringResource(R.string.road_lab), buildingId = 1000007,
            done = road.levels.lab.done,
            seconds = road.lab.seconds, remaining = road.lab, dayUnit = dayUnit, items = items('l'),
        )
        CategoryCard(
            title = stringResource(R.string.road_pets), buildingId = 1000068,
            done = road.levels.pets.done,
            seconds = road.pets.seconds, remaining = road.pets, dayUnit = dayUnit, items = items('p'),
        )

        Text(
            stringResource(R.string.road_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One group of upgrades: picture, name, share done, a bar, the time left (or "No timer") and the cost left. */
@Composable
private fun CategoryCard(
    title: String,
    buildingId: Int,
    done: Double?,
    /** Null for upgrades that take no time (walls). */
    seconds: Long?,
    remaining: RoadTotals,
    dayUnit: String,
    /** Every item of the group; tapping the card opens them as a grid of pictures with their levels. */
    items: List<RoadItem> = emptyList(),
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().wiggleOnTap(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().then(if (items.isNotEmpty()) Modifier.clickable { open = !open } else Modifier),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BuildingImage(buildingId, 40.dp)
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                Text(percentText(done), style = MaterialTheme.typography.titleMedium)
                if (items.isNotEmpty()) Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ProgressLine(done, large = false, showPercent = false)
            if (items.isNotEmpty()) {
                val left = items.sumOf { it.levelsLeft }
                Text(
                    stringResource(R.string.road_levels_left, left),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.road_time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                Text(
                    if (seconds != null && remaining.seconds > 0) formatDuration(seconds, dayUnit) else stringResource(R.string.road_no_timer),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.road_cost_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CostChip(R.drawable.road_gold, remaining.gold)
                    CostChip(R.drawable.road_elixir, remaining.elixir)
                    CostChip(R.drawable.road_dark_elixir, remaining.darkElixir)
                    if (remaining.goldOrElixir > 0) {
                        // Walls: Gold or Elixir, shown as the two drops together.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Image(painterResource(R.drawable.road_gold), contentDescription = null, modifier = Modifier.size(20.dp))
                            Image(painterResource(R.drawable.road_elixir), contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(compact(remaining.goldOrElixir), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (remaining.gold + remaining.elixir + remaining.darkElixir + remaining.goldOrElixir == 0L) {
                        Text("—", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            AnimatedVisibility(open && items.isNotEmpty()) { ItemGrid(items) }
        }
    }
}

/** The group's items as pictures: the level you have (the lowest copy), "×N" when there are several copies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemGrid(items: List<RoadItem>) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sorted = remember(items) { items.sortedWith(compareByDescending<RoadItem> { it.levelsLeft }.thenBy { it.id }) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    // As many tiles per row as fit at least 58 dp wide, then widened a little so the row has no leftover gap.
    val gap = 8.dp
    val columns = ((maxWidth + gap) / (58.dp + gap)).toInt().coerceAtLeast(1)
    val tile = (maxWidth - gap * (columns - 1)) / columns
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (item in sorted) {
            val picture = remember(item.id, item.lowest, tile) {
                WidgetIcons.get(context).bitmapForLevel(item.id, item.lowest.coerceAtLeast(1), with(density) { tile.roundToPx() })?.asImageBitmap()
            }
            val edge = if (item.maxed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier.size(tile).border(1.5.dp, edge, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
            ) {
                if (picture != null) Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
                // Crafted Defense modules share their defense's picture, so the module's initials tell them apart.
                if (item.group == 'c') item.name?.let { n ->
                    Text(
                        n.split(' ', '-').mapNotNull { it.firstOrNull() }.take(3).joinToString(""),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp),
                    )
                }
                if (item.count > 1) Text(
                    "×${item.count}", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
                )
                Text(
                    item.lowest.toString(), style = MaterialTheme.typography.labelMedium,
                    color = if (item.maxed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                        .background(if (item.maxed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
    }
}

/** Icon and amount, shown only when there is something to pay. */
@Composable
private fun CostChip(@DrawableRes icon: Int, amount: Long) {
    if (amount <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(compact(amount), style = MaterialTheme.typography.titleSmall)
    }
}

/** A bar with the percentage next to it; nothing to do means a full bar. */
@Composable
private fun ProgressLine(done: Double?, large: Boolean, showPercent: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // The same wavy bar the Timers cards use.
        LinearWavyProgressIndicator(progress = { (done ?: 1.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
        if (showPercent) {
            Text(
                percentText(done),
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Whole percent, rounded down so 99.6% never reads as finished. */
private fun percentText(done: Double?): String = "${((done ?: 1.0) * 100).toInt().coerceIn(0, 100)}%"

/** A building's picture from the bundled or downloaded icons; nothing when there is none. */
@Composable
private fun BuildingImage(buildingId: Int?, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap = remember(buildingId) {
        buildingId?.let { WidgetIcons.get(context).bitmapForLevel(it, null, with(density) { size.roundToPx() })?.asImageBitmap() }
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.iconTile(size)) else Spacer(Modifier.size(size))
}

/** One line of the card: a small picture (a building from the bundled icons, or a resource drawable), label and value. */
@Composable
private fun RoadRow(
    label: String,
    value: String,
    detail: String? = null,
    buildingId: Int? = null,
    @DrawableRes iconRes: Int? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val building = remember(buildingId) {
        buildingId?.let { WidgetIcons.get(context).bitmapForLevel(it, null, with(density) { 32.dp.roundToPx() })?.asImageBitmap() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            building != null -> Image(building, contentDescription = null, modifier = Modifier.iconTile(32.dp))
            iconRes != null -> Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(32.dp).padding(2.dp))
            else -> Spacer(Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** "1,706 d" for a day or more, otherwise the usual clock text. */
private fun formatDuration(seconds: Long, dayUnit: String): String {
    val days = seconds / 86_400
    return if (days >= 1) String.format(Locale.US, "%,d %s", days, dayUnit) else Countdown.text(seconds, dayUnit)
}

/** 2,924,000,000 -> "2.92B", 47,000,000 -> "47.0M", 12,500 -> "12.5K". */
internal fun compact(n: Long): String = when {
    n >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> n.toString()
}
