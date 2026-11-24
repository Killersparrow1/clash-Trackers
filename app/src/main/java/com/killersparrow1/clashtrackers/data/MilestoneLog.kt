package com.killersparrow1.clashtrackers.data

import android.content.Context
import com.killersparrow1.clashtrackers.core.NameMap
import com.killersparrow1.clashtrackers.core.RoadToMaxCalc
import com.killersparrow1.clashtrackers.core.VillageExport

/**
 * "Latest maxed": every sync compares the items that are maxed now with the ones that were maxed in the previous export,
 * and remembers the new ones with the time. Plain text in SharedPreferences, newest first, a few dozen per account.
 */
object MilestoneLog {
    data class Entry(val id: Int, val name: String, val level: Int, val at: Long)

    private const val PREFS = "milestones"
    private const val KEEP = 30

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(tag: String) = "log_$tag"

    /** Items (not walls) maxed in [now] that were still below max in [old]. Empty if the Town Hall changed or there is no earlier export. */
    fun newlyMaxed(context: Context, old: VillageExport?, now: VillageExport): List<Pair<Int, Int>> {
        if (old == null || old.townHallLevel != now.townHallLevel) return emptyList()
        val data = MaxDataStore.get(context)
        val before = RoadToMaxCalc.compute(data, old)?.items?.associateBy { it.id } ?: return emptyList()
        val after = RoadToMaxCalc.compute(data, now)?.items ?: return emptyList()
        return after.filter { it.group != 'w' && it.maxed && before[it.id]?.maxed == false }.map { it.id to it.maxLevel }
    }

    fun record(context: Context, tag: String, old: VillageExport?, now: VillageExport, names: NameMap, nowSec: Long) {
        val fresh = newlyMaxed(context, old, now)
        if (fresh.isEmpty()) return
        val added = fresh.map { (id, level) -> Entry(id, names.name(id), level, nowSec) }
        val all = (added + recent(context, tag, KEEP)).take(KEEP)
        prefs(context).edit().putString(key(tag), all.joinToString("\n") { "${it.id}|${it.level}|${it.at}|${it.name.replace('|', ' ').replace('\n', ' ')}" }).apply()
    }

    fun recent(context: Context, tag: String, count: Int): List<Entry> =
        prefs(context).getString(key(tag), null).orEmpty().lines().mapNotNull { line ->
            val p = line.split('|', limit = 4)
            if (p.size < 4) null else Entry(p[0].toIntOrNull() ?: return@mapNotNull null, p[3], p[1].toIntOrNull() ?: 0, p[2].toLongOrNull() ?: 0L)
        }.take(count)
}
