package com.killersparrow1.clashtrackers.ui

import com.killersparrow1.clashtrackers.R
import androidx.annotation.StringRes
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.core.WidgetRow

enum class TimerFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    BUILDERS(R.string.filter_builders),
    LAB(R.string.filter_lab),
    PETS(R.string.filter_pets),
    BUILDER_BASE(R.string.filter_bb),
}

data class DashboardTimer(val category: TimerFilter, val row: WidgetRow)

/** Presentation only: predicted times and completion state come from TimerEngine & WidgetModelBuilder. */
fun WidgetModel.timers(filter: TimerFilter): List<DashboardTimer> {
    if (!hasData) return emptyList()
    val all = mutableListOf<DashboardTimer>()
    all.addAll(builderRows.map { DashboardTimer(TimerFilter.BUILDERS, it) })
    all.addAll(labRows.map { DashboardTimer(TimerFilter.LAB, it) })
    all.add(DashboardTimer(TimerFilter.PETS, petRow))
    if (showBuilderBase || bbRows.isNotEmpty() || starLabRow != null) {
        all.addAll(bbRows.map { DashboardTimer(TimerFilter.BUILDER_BASE, it) })
        starLabRow?.let { all.add(DashboardTimer(TimerFilter.BUILDER_BASE, it)) }
    }
    return all
        .filter { filter == TimerFilter.ALL || it.category == filter }
        .sortedWith(compareBy<DashboardTimer> { it.row.kind.ordinal }.thenBy { it.row.finishAt ?: Long.MAX_VALUE })
}

fun WidgetModel.nextRunningTimer(): DashboardTimer? =
    timers(TimerFilter.ALL).firstOrNull { it.row.kind == RowKind.RUNNING }

