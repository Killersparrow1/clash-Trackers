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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
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

private val DefaultDayWidth = 132.dp
private val MinDayWidth = 40.dp
private val MaxDayWidth = 720.dp
/** Upgrades queued per slot, and how far ahead (days) they are suggested. */
private const val QueueMax = 12
private const val QueueDays = 21L
private val LabelWidth = 84.dp
private val HeaderHeight = 48.dp
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
 * your sleep window, so they are estimates.
 */
@Composable
internal fun TimerCalendar(model: WidgetModel, export: VillageExport?, now: Long, filter: TimerFilter) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val dayUnit = stringResource(R.string.time_day_short)
    val names = remember { Names.get(context) }
    val data = remember(MaxDataStore.version) { MaxDataStore.get(context) }
    val window = remember { SleepStore.get(context) }
    val slotText = stringResource(R.string.calendar_builder_short)
    val labText = stringResource(R.string.calendar_lab_short)
    val petsText = stringResource(R.string.calendar_pets_short)
    val goblinText = stringResource(R.string.cat_goblin_builder)
    val starLabText = stringResource(R.string.plan_star_lab)
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
            val plan = Bedtime.plan(data, export, slot, freeAt, window, zone, limit = 40) ?: return null
            val pick = plan.items.firstOrNull { (used[it.id to it.level] ?: 0) < it.count } ?: return null
            used[pick.id to pick.level] = (used[pick.id to pick.level] ?: 0) + 1
            val title = "${names.name(pick.id)} ➛ ${pick.level}"
            return Bar(plan.startAt, plan.startAt + pick.seconds, title, nextText, listOf(pick.id), pick.level, true)
        }
        fun lanes(rows: List<WidgetRow>, slot: PlanSlot?, label: (Int, WidgetRow) -> String): List<Lane> =
            rows.mapIndexed { i, row ->
                val run = running(row)
                val bars = ArrayList<Bar>()
                run?.let { bars += it }
                // Upgrades queued one after another, each starting when the one before it ends, up to QueueDays ahead.
                var free = run?.end ?: now
                if (slot != null) {
                    var n = 0
                    while (n < QueueMax && free < now + QueueDays * 86_400L) {
                        val up = next(slot, free) ?: break
                        bars += up
                        free = up.end
                        n++
                    }
                }
                Lane(label(i, row), bars)
            }
        // A slot with nothing running: its queue starts now.
        fun idleLane(label: String, slot: PlanSlot): Lane {
            val bars = ArrayList<Bar>()
            var free = now
            var n = 0
            while (n < QueueMax && free < now + QueueDays * 86_400L) {
                val up = next(slot, free) ?: break
                bars += up
                free = up.end
                n++
            }
            return Lane(label, bars)
        }
        buildList {
            if (filter == TimerFilter.ALL || filter == TimerFilter.BUILDERS) {
                add(Section(titles[0], 0, lanes(model.builderRows, PlanSlot.BUILDER) { i, r -> if (r.goblin) goblinText else slotText.format(i + 1) }))
            }
            if (filter == TimerFilter.ALL || filter == TimerFilter.LAB) {
                add(Section(titles[1], 1, lanes(model.labRows, PlanSlot.LAB) { i, _ -> if (model.labRows.size > 1) "$labText ${i + 1}" else labText }))
            }
            if (filter == TimerFilter.ALL || filter == TimerFilter.PETS) {
                add(Section(titles[2], 2, lanes(listOf(model.petRow), PlanSlot.PET) { _, _ -> petsText }))
            }
            if (filter == TimerFilter.ALL || filter == TimerFilter.BUILDER_BASE) {
                val hasBb = export != null && Bedtime.builderHall(export) != null
                val builders = lanes(model.bbRows, if (hasBb) PlanSlot.BB_BUILDER else null) { i, _ -> slotText.format(i + 1) }
                // Only running upgrades are known, so two Builder Base builders are assumed when fewer are busy.
                val idle = if (hasBb) (builders.size until 2).map { idleLane(slotText.format(it + 1), PlanSlot.BB_BUILDER) } else emptyList()
                val starLabRow = model.starLabRow
                val starLab: List<Lane> = when {
                    starLabRow != null -> lanes(listOf(starLabRow), if (hasBb) PlanSlot.STAR_LAB else null) { _, _ -> starLabText }
                    hasBb -> listOf(idleLane(starLabText, PlanSlot.STAR_LAB))
                    else -> emptyList()
                }
                val all = builders + idle + starLab
                if (all.isNotEmpty()) add(Section(titles[3], 3, all))
            }
        }
    }

    val t0 = remember(now / 3600) { Instant.ofEpochSecond(now).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond() }
    val lastEnd = sections.maxOfOrNull { s -> s.lanes.maxOfOrNull { l -> l.bars.maxOfOrNull { it.end } ?: 0L } ?: 0L } ?: 0L
    val days = (((maxOf(lastEnd, now + 2 * 86_400L) - t0) / 86_400L) + 1).toInt().coerceIn(3, 45)
    // Zoom: pinch, or the + / - buttons. Zoomed in far enough, hour labels appear.
    var dayDp by rememberSaveable { mutableFloatStateOf(DefaultDayWidth.value) }
    val DayWidth = dayDp.dp
    val hourStep = when {
        dayDp >= 560f -> 1
        dayDp >= 280f -> 3
        dayDp >= 170f -> 6
        dayDp >= 110f -> 12
        else -> 0
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableIntStateOf(0) }
    fun zoomBy(factor: Float) {
        val old = dayDp
        val new = (old * factor).coerceIn(MinDayWidth.value, MaxDayWidth.value)
        if (new == old) return
        dayDp = new
        // Keep the middle of the visible part where it is.
        val ratio = new / old
        val target = ((scroll.value + viewport / 2f) * ratio - viewport / 2f).toInt().coerceAtLeast(0)
        scope.launch { withFrameNanos { }; scroll.scrollTo(target) }
    }
    val hourWidth = DayWidth / 24f
    fun xOf(t: Long): Dp = hourWidth * ((t - t0) / 3600f)

    val locale = Locale.getDefault()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEd"), locale) }
    val hourFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "j"), locale) }
    val offlineShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val showTitles = sections.size > 1
    val sectionH = if (showTitles) SectionHeight else 0.dp
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val nowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val density = LocalDensity.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            // Slot names stay in place while the days scroll.
            Column(Modifier.width(LabelWidth)) {
                Spacer(HeaderHeight)
                sections.forEach { s ->
                    if (showTitles) Box(Modifier.height(sectionH).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
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
                Modifier
                    .weight(1f)
                    .onSizeChanged { viewport = it.width }
                    .pinchZoom { zoomBy(it) }
                    .horizontalScroll(scroll),
            ) {
                Column(
                    Modifier
                        .width(DayWidth * days)
                        .drawBehind {
                            val dayPx = DayWidth.toPx()
                            // Offline: the sleep window of every day, a little darker than the rest.
                            if (window.startMin != window.endMin) {
                                val minute = dayPx / 1440f
                                val top = HeaderHeight.toPx()
                                fun band(from: Int, to: Int, day: Int) = drawRect(
                                    offlineShade, Offset(day * dayPx + from * minute, top),
                                    androidx.compose.ui.geometry.Size((to - from) * minute, size.height - top),
                                )
                                for (d in 0 until days) {
                                    if (window.startMin < window.endMin) band(window.startMin, window.endMin, d)
                                    else { band(0, window.endMin, d); band(window.startMin, 1440, d) }
                                }
                            }
                            for (d in 0..days) drawLine(lineColor, Offset(d * dayPx, 0f), Offset(d * dayPx, size.height), 1.dp.toPx())
                            if (hourStep > 0) {
                                val faint = lineColor.copy(alpha = lineColor.alpha * 0.5f)
                                for (d in 0 until days) for (h in hourStep until 24 step hourStep) {
                                    val x = d * dayPx + dayPx * h / 24f
                                    drawLine(faint, Offset(x, HeaderHeight.toPx()), Offset(x, size.height), 1.dp.toPx())
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val x = with(density) { xOf(now).toPx() }
                            drawLine(nowColor, Offset(x, HeaderHeight.toPx()), Offset(x, size.height), 2.dp.toPx())
                            drawCircle(nowColor, 5.dp.toPx(), Offset(x, HeaderHeight.toPx()))
                        },
                ) {
                    Row(Modifier.height(HeaderHeight)) {
                        for (d in 0 until days) {
                            val label = dayFormat.format(Instant.ofEpochSecond(t0 + d * 86_400L).atZone(zone))
                            Box(Modifier.width(DayWidth).fillMaxHeight()) {
                                Text(
                                    label, style = MaterialTheme.typography.labelLarge,
                                    color = if (d == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp),
                                )
                                if (hourStep > 0) for (h in hourStep until 24 step hourStep) {
                                    Text(
                                        java.time.LocalTime.of(h, 0).format(hourFormat), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                        modifier = Modifier.align(Alignment.BottomStart).offset(x = hourWidth * h.toFloat() + 3.dp).padding(bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    sections.forEach { s ->
                        if (showTitles) Spacer(sectionH)
                        s.lanes.forEach { lane ->
                            Box(Modifier.height(LaneHeight).fillMaxWidth()) {
                                // Nothing running: the stretch before the first suggested upgrade starts is idle time.
                                val first = lane.bars.firstOrNull()
                                if (first != null && first.suggested && first.start > now + 60) IdleHatch(xOf(now), xOf(first.start))
                                lane.bars.forEach { bar -> CalendarBar(bar, s.kind, now, dayUnit, xOf(maxOf(bar.start, t0)), xOf(bar.end)) }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.calendar_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                val scheme = MaterialTheme.colorScheme
                LegendItem(stringResource(R.string.legend_builder), scheme.primaryContainer, scheme.primary)
                LegendItem(stringResource(R.string.legend_lab), scheme.tertiaryContainer, scheme.tertiary)
                LegendItem(stringResource(R.string.legend_next), scheme.surfaceContainerHigh, scheme.outline)
                LegendItem(stringResource(R.string.legend_offline), offlineShade, Color.Transparent)
                LegendItem(stringResource(R.string.legend_idle), scheme.error.copy(alpha = 0.35f), Color.Transparent)
            }
            FilledTonalIconButton(onClick = { zoomBy(1f / 1.5f) }, enabled = dayDp > MinDayWidth.value) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            FilledTonalIconButton(onClick = { zoomBy(1.5f) }, enabled = dayDp < MaxDayWidth.value) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, fill: Color, border: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(20.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(fill).border(BorderStroke(1.dp, border), RoundedCornerShape(4.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Red diagonal stripes from [left] to [right]: a slot with nothing running. */
@Composable
private fun IdleHatch(left: Dp, right: Dp) {
    val stripe = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .offset(x = left, y = 6.dp)
            .width((right - left).coerceAtLeast(8.dp))
            .height(LaneHeight - 12.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, stripe), shape)
            .drawBehind {
                val step = 10.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                    x += step
                }
            },
    )
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
    // Text takes the colour made for its background, so it reads on every theme (a suggestion sits on the plain surface).
    val onBar: Color = if (bar.suggested) scheme.onSurface else when (kind) {
        1 -> scheme.onTertiaryContainer
        2 -> scheme.onSecondaryContainer
        3 -> scheme.onSurface
        else -> scheme.onPrimaryContainer
    }
    val width = (right - left).coerceAtLeast(40.dp)
    val shape = RoundedCornerShape(12.dp)
    val edge: Color = when (kind) {
        1 -> scheme.tertiary
        2 -> scheme.secondary
        3 -> scheme.outline
        else -> scheme.primary
    }
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
            .background(if (bar.suggested) scheme.surfaceContainerHigh else base)
            .border(BorderStroke(if (bar.suggested) 1.5.dp else 1.dp, if (bar.suggested) scheme.outline else edge.copy(alpha = 0.7f)), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.iconTile(28.dp))
        Column(Modifier.weight(1f)) {
            Text(bar.title, style = MaterialTheme.typography.labelLarge, color = onBar, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (bar.suggested) "${bar.sub} · ${Countdown.text(bar.end - bar.start, dayUnit)}" else Countdown.text((bar.end - now).coerceAtLeast(0), dayUnit)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = onBar.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Two-finger pinch reported as a zoom factor; the fingers are consumed so the list does not scroll while pinching. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
