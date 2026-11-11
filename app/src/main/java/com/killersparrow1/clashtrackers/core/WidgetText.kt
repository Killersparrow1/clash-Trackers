package com.killersparrow1.clashtrackers.core

import java.util.Locale
import kotlin.math.ceil

/**
 * User-facing text produced by the widget model. English is the default so the model stays
 * Android-free and existing tests keep their expectations; the app builds a localized copy from
 * resources before calling [WidgetModelBuilder.build].
 */
data class WidgetText(
    val day: String = "d",
    val hour: String = "h",
    val minute: String = "m",
    val justNow: String = "just now",
    val ago: String = "ago",
    val completed: String = "Completed",
    val idle: String = "Idle",
    val builders: String = "BUILDERS",
    val goblinSuffix: String = " + Goblin",
    val goblin: String = "GOBLIN",
    val bite: String = "Bite",
    val soup: String = "Soup",
    val plusOne: String = "+1",
    val syncFailed: String = "Sync failed — copy data and tap Sync",
    val noData: String = "Copy village data in Clash, then tap Sync",
    val synced: String = "synced",
    val helpers: String = "Helpers: %1\$s · %2\$s",
    val apprenticeName: String = "Apprentice: %1\$s",
    val assistantName: String = "Assistant: %1\$s",
    val apprenticeIdle: String = "Apprentice idle",
    val assistantIdle: String = "Assistant idle",
    val laboratory: String = "Laboratory",
    val petHouse: String = "Pet House",
    val newSuffix: String = "(new)",
    val unknownName: String = "Unknown (%1\$d)",
    val craftedDefense: String = "Crafted defense",
    val moduleLabel: String = "%1\$s %2\$s",
    val townHallTag: String = "TH%1\$d",
) {
    fun formatRemaining(seconds: Long): String {
        val minutes = (seconds + 59) / 60
        val d = minutes / (24 * 60)
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return when {
            d > 0 -> "$d$day $h$hour"
            h > 0 -> "$h$hour $m$minute"
            else -> "$m$minute"
        }
    }

    fun formatAgo(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 60 -> justNow
            s < 3600 -> "${s / 60}$minute $ago"
            s < 2 * 86400 -> "${s / 3600}$hour $ago"
            else -> "${s / 86400}$day $ago"
        }
    }

    /** Pill text: `42m`, `1h 50m`, `2h`. Minutes are rounded up so a running boost never shows `0m`. */
    fun formatPill(seconds: Long): String {
        val minutes = ceil(seconds / 60.0).toLong().coerceAtLeast(1)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m$minute"
            m == 0L -> "$h$hour"
            else -> "$h$hour $m$minute"
        }
    }

    fun formatHelpers(apprentice: String, assistant: String): String =
        String.format(Locale.ROOT, helpers, apprentice, assistant)

    fun formatApprentice(name: String): String = String.format(Locale.ROOT, apprenticeName, name)

    fun formatAssistant(name: String): String = String.format(Locale.ROOT, assistantName, name)

    fun formatUnknown(id: Int): String = String.format(Locale.ROOT, unknownName, id)

    fun formatModule(defense: String, module: String): String =
        String.format(Locale.ROOT, moduleLabel, defense, module)

    fun formatTownHall(level: Int): String = String.format(Locale.ROOT, townHallTag, level)

    companion object {
        val EN = WidgetText()
    }
}
