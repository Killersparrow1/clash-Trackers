@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import kotlinx.coroutines.launch

private const val HINTS = "ui_hints"
private const val KEY_CALENDAR_TIP = "calendar_tip_seen"

private fun tipSeen(context: Context): Boolean =
    runCatching { context.getSharedPreferences(HINTS, Context.MODE_PRIVATE).getBoolean(KEY_CALENDAR_TIP, false) }.getOrDefault(true)

private fun markTipSeen(context: Context) {
    runCatching { context.getSharedPreferences(HINTS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CALENDAR_TIP, true).apply() }
}

/**
 * The header of the upgrade list: the title on the left, a compact calendar switch opposite it, and a row of slim
 * category pills underneath. The first time the switch is used, a short tooltip says what it does.
 */
@Composable
fun UpgradesHeader(
    filter: TimerFilter,
    onFilter: (TimerFilter) -> Unit,
    calendar: Boolean,
    onCalendar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.village_upgrades), style = MaterialTheme.typography.titleMediumEmphasized)
            CalendarSwitch(calendar, onCalendar)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerFilter.entries.forEach { entry ->
                CategoryPill(stringResource(entry.labelRes), selected = filter == entry) { onFilter(entry) }
            }
        }
    }
}

/** A slim outlined pill; the selected one is filled. */
@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.height(30.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The calendar switch with a calendar icon in its thumb, and a one-time tooltip on first use. */
@Composable
private fun CalendarSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tooltip = rememberTooltipState(isPersistent = true)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                title = { Text(stringResource(R.string.calendar_tip_title)) },
                action = { TextButton(onClick = { scope.launch { tooltip.dismiss() } }) { Text(stringResource(R.string.calendar_tip_ok)) } },
            ) { Text(stringResource(R.string.calendar_tip_body)) }
        },
        state = tooltip,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { on ->
                onChange(on)
                // First time only: explain what the switch does.
                if (!tipSeen(context)) {
                    markTipSeen(context)
                    scope.launch { tooltip.show() }
                }
            },
            thumbContent = {
                Icon(painterResource(R.drawable.ic_clash), contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )
    }
}
