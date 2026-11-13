package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.RoadToMaxCalc
import com.killersparrow1.clashtrackers.core.RoadTotals
import com.killersparrow1.clashtrackers.core.VillageExport
import com.killersparrow1.clashtrackers.data.MaxDataStore
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
            stringResource(R.string.road_builders),
            formatDuration(builderWork.seconds, dayUnit),
            if (builders > 1 && builderWork.seconds > 0)
                stringResource(R.string.road_builders_split, formatDuration(builderWork.seconds / builders, dayUnit), builders)
            else null,
        )
        RoadRow(stringResource(R.string.road_lab), formatDuration(road.lab.seconds, dayUnit))
        RoadRow(stringResource(R.string.road_pets), formatDuration(road.pets.seconds, dayUnit))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.road_cost), style = MaterialTheme.typography.titleSmall)
            RoadRow(stringResource(R.string.resource_gold), compact(all.gold))
            RoadRow(stringResource(R.string.resource_elixir), compact(all.elixir))
            RoadRow(stringResource(R.string.resource_dark_elixir), compact(all.darkElixir))
            if (includeWalls) RoadRow(stringResource(R.string.road_walls_cost), compact(road.walls.goldOrElixir))
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

@Composable
private fun ColumnScope.RoadRow(label: String, value: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 0.dp))
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
