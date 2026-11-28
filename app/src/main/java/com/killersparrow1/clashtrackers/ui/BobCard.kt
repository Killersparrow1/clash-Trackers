package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.BobKind
import com.killersparrow1.clashtrackers.core.BobPlan
import com.killersparrow1.clashtrackers.core.BobRow
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import java.util.Locale

private fun duration(seconds: Long, dayUnit: String): String {
    val days = seconds / 86_400
    return if (days >= 1) String.format(Locale.US, "%,d %s", days, dayUnit) else Countdown.text(seconds, dayUnit)
}

/** A small "B.O.B. 3/7" chip for the Bedtime header; tap it for the requirements and what to upgrade first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BobChip(plan: BobPlan) {
    var open by remember { mutableStateOf(false) }
    val dayUnit = stringResource(R.string.time_day_short)
    val names = Names.get(LocalContext.current)
    AssistChip(
        onClick = { open = true },
        label = {
            Text(
                if (plan.unlocked) stringResource(R.string.bob_chip_unlocked)
                else stringResource(R.string.bob_chip),
            )
        },
    )
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // No stretch effect at the ends: it made the sheet jiggle when pulling it down to close. The scroll blur follows the content.
            val sheetScroll = rememberScrollState()
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
            Column(
                Modifier.fillMaxWidth().scrollBlur(rememberScrollBlur(sheetScroll)).verticalScroll(sheetScroll).padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.bob_title), style = MaterialTheme.typography.titleLarge)
                if (plan.unlocked) {
                    Text(stringResource(R.string.bob_unlocked), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        stringResource(
                            R.string.bob_total,
                            duration(plan.remainingSeconds, dayUnit), compact(plan.builderGold), compact(plan.gold),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val first = plan.next
                plan.rows.forEach { BobRowView(it, highlight = !plan.unlocked && it == first, dimmed = !plan.unlocked && first != null && !it.done && it != first, names::name, dayUnit) }
                Text(stringResource(R.string.bob_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        }
    }
}

@Composable
private fun rowTitle(row: BobRow, name: (Int) -> String): String = when (row.kind) {
    BobKind.GEAR_UP -> stringResource(R.string.bob_gear_up, name(row.itemId))
    BobKind.TROOP -> stringResource(R.string.bob_troop)
    BobKind.DEFENCE -> stringResource(R.string.bob_defence)
    BobKind.HEROES -> stringResource(R.string.bob_heroes)
    BobKind.CONTROL -> stringResource(R.string.bob_control)
}

@Composable
private fun BobRowView(row: BobRow, highlight: Boolean, dimmed: Boolean, name: (Int) -> String, dayUnit: String) {
    val context = LocalContext.current
    val picture = remember(row.itemId) {
        if (row.itemId == 0) null else WidgetIcons.get(context).bitmapForLevel(row.itemId, null, 96)?.asImageBitmap()
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.padding(16.dp).let { if (dimmed) it.then(Modifier) else it },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (picture != null) Image(picture, contentDescription = null, modifier = Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val muted = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Text(rowTitle(row, name), style = MaterialTheme.typography.titleMedium, color = muted)
                val detail = when {
                    row.done -> stringResource(R.string.bob_done)
                    row.kind == BobKind.GEAR_UP -> stringResource(R.string.bob_gear_up_left, duration(row.seconds, dayUnit), compact(row.cost))
                    row.kind == BobKind.HEROES -> stringResource(R.string.bob_row_levels, row.have, row.need) +
                        (if (row.machineTarget > 0) " · " + stringResource(R.string.bob_hero_split, row.machineTarget, row.copterTarget) else "")
                    else -> stringResource(R.string.bob_row_levels, row.have, row.need)
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!row.done && (row.kind == BobKind.TROOP || row.kind == BobKind.DEFENCE) && row.itemId != 0) {
                    Text(stringResource(R.string.bob_quickest, name(row.itemId)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!row.done && row.kind != BobKind.GEAR_UP && (row.seconds > 0 || row.cost > 0)) {
                    Text(
                        listOfNotNull(
                            row.seconds.takeIf { it > 0 }?.let { duration(it, dayUnit) },
                            row.cost.takeIf { it > 0 }?.let { stringResource(R.string.bob_builder_gold, compact(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                row.needsBuilderHall?.takeIf { !row.done }?.let {
                    Text(stringResource(R.string.bob_needs_bh, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
