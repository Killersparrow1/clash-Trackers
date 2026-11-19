package com.killersparrow1.clashtrackers.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.Bedtime
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.PlanSlot
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.VillageExport
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.core.WidgetRow
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.sync.SleepStore
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayWidth = 132.dp
private val LabelWidth = 84.dp
private val HeaderHeight = 32.dp
private val SectionHeight = 36.dp
private val LaneHeight = 56.dp

/** One bar on a slot's line: a running upgrade, or the next one suggested by Bedtime level up. */
private class Bar(
    val start: Long,
    val end: Long,
    val title: String,
    val sub: String,
    val iconIds: List<Int>,
    val iconLevel: Int?,
    val suggested: Boolean,
)

private class Lane(val label: String, val bars: List<Bar>)

private class Section(val title: String, val kind: Int, val lanes: List<Lane>)

/**
 * The Timers as a calendar: one line per builder, lab or pet slot, every running upgrade drawn from its start to its
 * finish, and (dashed) the first upgrade Bedtime level up would start next in that slot. Suggestions use base times and
 * your sleep window, so they are estimates; Builder Base has no suggestions.
 */
@Composable
internal fun TimerCalendar(model: WidgetModel, export: VillageExport?, now: Long, filter: TimerFilter) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val dayUnit = stringResource(R.string.time_day_short)
    val names = remember { Names.get(context) }
    val data = remember(MaxDataStore.version) { MaxDataStore.get(context) }
    val window = remember { SleepStore.get(context) }
    val slotText = stringResource(R.string.calendar_slot)
    val goblinText = stringResource(R.string.cat_goblin_builder)
    val nextText = stringResource(R.string.calendar_next)
    val titles = listOf(
        stringResource(R.string.plan_builders), stringResource(R.string.plan_lab),
        stringResource(R.string.plan_pets), stringResource(R.string.filter_bb),
    )

    // Suggestions are recomputed once a minute, not every second.
    val sections = remember(export, filter, now / 60, window) {
        fun running(row: WidgetRow): Bar? {
            val end = row.finishAt ?: return null
            if (row.kind != RowKind.RUNNING) return null
            val total = if (row.progress < 0.999f) (row.remaining / (1f - row.progress)).toLong() else row.remaining
            return Bar(end - total, end, row.label.replace(" → ", " ➛ "), "", row.iconIds, row.iconLevel, false)
        }
        val used = HashMap<Pair<Int, Int>, Int>()
        fun next(slot: PlanSlot, freeAt: Long): Bar? {
            if (export == null) return null
            val plan = Bedtime.plan(data, export, slot, freeAt, window, zone, limit = 12) ?: return null
            val pick = plan.items.firstOrNull { (used[it.id to it.level] ?: 0) < it.count } ?: return null
            used[pick.id to pick.level] = (used[pick.id to pick.level] ?: 0) + 1
            val title = "${names.name(pick.id)} ➛ ${pick.level}"
            return Bar(plan.startAt, plan.startAt + pick.seconds, title, nextText, listOf(pick.id), pick.level, true)
        }
        fun lanes(rows: List<WidgetRow>, slot: PlanSlot?, label: (Int, WidgetRow) -> String): List<Lane> =
            rows.mapIndexed { i, row ->
                val run = running(row)
                val free = run?.end ?: now
                val upNext = slot?.let { next(it, free) }
                Lane(label(i, row), listOfNotNull(run, upNext))
            }
        buildList {
            if (filter == TimerFilter.ALL || filter == TimerFilter.BUILDERS) {
                add(Section(titles[0], 0, lanes(model.builderRows, PlanSlot.BUILDER) { i, r -> if (r.goblin) goblinText else slotText.format(i + 1) }))
            }
            if (filter == TimerFilter.ALL || filter == TimerFilter.LAB) {
                add(Section(titles[1], 1, lanes(model.labRows, PlanSlot.LAB) { i, _ -> slotText.format(i + 1) }))
            }
            if (filter == TimerFilter.ALL || filter == TimerFilter.PETS) {
                add(Section(titles[2], 2, lanes(listOf(model.petRow), PlanSlot.PET) { i, _ -> slotText.format(i + 1) }))
            }
            if (filter == TimerFilter.ALL || filter == TimerFilter.BUILDER_BASE) {
                val bb = model.bbRows + listOfNotNull(model.starLabRow)
                if (bb.isNotEmpty()) add(Section(titles[3], 3, lanes(bb, null) { i, _ -> slotText.format(i + 1) }))
            }
        }
    }

    val t0 = remember(now / 3600) { Instant.ofEpochSecond(now).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond() }
    val lastEnd = sections.maxOfOrNull { s -> s.lanes.maxOfOrNull { l -> l.bars.maxOfOrNull { it.end } ?: 0L } ?: 0L } ?: 0L
    val days = (((maxOf(lastEnd, now + 2 * 86_400L) - t0) / 86_400L) + 1).toInt().coerceIn(3, 45)
    val hourWidth = DayWidth / 24f
    fun xOf(t: Long): Dp = hourWidth * ((t - t0) / 3600f)

    val locale = Locale.getDefault()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale) }
    val today = stringResource(R.string.calendar_today)
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val nowColor = MaterialTheme.colorScheme.error
    val density = LocalDensity.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            // Slot names stay in place while the days scroll.
            Column(Modifier.width(LabelWidth)) {
                Spacer(HeaderHeight)
                sections.forEach { s ->
                    Box(Modifier.height(SectionHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(s.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    s.lanes.forEach { l ->
                        Box(Modifier.height(LaneHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Text(l.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier
                        .width(DayWidth * days)
                        .drawBehind {
                            val dayPx = DayWidth.toPx()
                            for (d in 0..days) drawLine(lineColor, Offset(d * dayPx, 0f), Offset(d * dayPx, size.height), 1.dp.toPx())
                        }
                        .drawWithContent {
                            drawContent()
                            val x = with(density) { xOf(now).toPx() }
                            drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        },
                ) {
                    Row(Modifier.height(HeaderHeight)) {
                        for (d in 0 until days) {
                            val label = if (d == 0) today else dayFormat.format(Instant.ofEpochSecond(t0 + d * 86_400L).atZone(zone))
                            Box(Modifier.width(DayWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelLarge, color = if (d == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    sections.forEach { s ->
                        Spacer(SectionHeight)
                        s.lanes.forEach { lane ->
                            Box(Modifier.height(LaneHeight).fillMaxWidth()) {
                                lane.bars.forEach { bar -> CalendarBar(bar, s.kind, now, dayUnit, xOf(maxOf(bar.start, t0)), xOf(bar.end)) }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.calendar_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Spacer(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(height))

@Composable
private fun CalendarBar(bar: Bar, kind: Int, now: Long, dayUnit: String, left: Dp, right: Dp) {
    val scheme = MaterialTheme.colorScheme
    val base: Color = when (kind) {
        1 -> scheme.tertiaryContainer
        2 -> scheme.secondaryContainer
        3 -> scheme.surfaceContainerHighest
        else -> scheme.primaryContainer
    }
    val width = (right - left).coerceAtLeast(40.dp)
    val shape = RoundedCornerShape(14.dp)
    val context = LocalContext.current
    val density = LocalDensity.current
    val icon = remember(bar.iconIds, bar.iconLevel) {
        val icons = WidgetIcons.get(context)
        bar.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, bar.iconLevel, with(density) { 28.dp.roundToPx() }) }?.asImageBitmap()
    }
    Row(
        Modifier
            .offset(x = left, y = 6.dp)
            .width(width)
            .height(LaneHeight - 12.dp)
            .clip(shape)
            .background(if (bar.suggested) base.copy(alpha = 0.35f) else base)
            .then(if (bar.suggested) Modifier.border(BorderStroke(1.5.dp, scheme.outline), shape) else Modifier)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.iconTile(28.dp))
        Column(Modifier.weight(1f)) {
            Text(bar.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (bar.suggested) "${bar.sub} · ${Countdown.text(bar.end - bar.start, dayUnit)}" else Countdown.text((bar.end - now).coerceAtLeast(0), dayUnit)
            Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
