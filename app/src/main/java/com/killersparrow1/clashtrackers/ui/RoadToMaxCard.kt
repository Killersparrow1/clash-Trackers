package com.killersparrow1.clashtrackers.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
 * bundled game data. Estimates: base times and costs, no potions, helpers, events or Gold Pass.
 */
@Composable
internal fun RoadToMaxCard(export: VillageExport?, builders: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data = remember { MaxDataStore.get(context) }
    val road = remember(export, data) { export?.let { RoadToMaxCalc.compute(data, it) } } ?: return
    var includeWalls by rememberSaveable { mutableStateOf(false) }
    val dayUnit = stringResource(R.string.time_day_short)

    val builderWork = road.builderWork
    val all = builderWork + road.lab + road.pets
    CollapsibleCard(stringResource(R.string.road_title, road.townHall), "road_to_max", modifier) {
        RoadRow(
            label = stringResource(R.string.road_builders),
            value = formatDuration(builderWork.seconds / builders.coerceAtLeast(1), dayUnit),
            detail = if (builders > 1 && builderWork.seconds > 0)
                stringResource(R.string.road_builders_work, builders, formatDuration(builderWork.seconds, dayUnit))
            else null,
            buildingId = 1000015,
        )
        RoadRow(stringResource(R.string.road_lab), formatDuration(road.lab.seconds, dayUnit), buildingId = 1000007)
        RoadRow(stringResource(R.string.road_pets), formatDuration(road.pets.seconds, dayUnit), buildingId = 1000068)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.road_cost), style = MaterialTheme.typography.titleSmall)
            RoadRow(stringResource(R.string.resource_gold), compact(all.gold), iconRes = R.drawable.road_gold)
            RoadRow(stringResource(R.string.resource_elixir), compact(all.elixir), iconRes = R.drawable.road_elixir)
            RoadRow(stringResource(R.string.resource_dark_elixir), compact(all.darkElixir), iconRes = R.drawable.road_dark_elixir)
            if (includeWalls) RoadRow(stringResource(R.string.road_walls_cost), compact(road.walls.goldOrElixir), iconRes = R.drawable.road_gold)
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = includeWalls, role = Role.Switch, onValueChange = { includeWalls = it }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.road_include_walls), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = includeWalls, onCheckedChange = null)
        }
        Text(
            stringResource(R.string.road_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            building != null -> Image(building, contentDescription = null, modifier = Modifier.size(32.dp))
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
