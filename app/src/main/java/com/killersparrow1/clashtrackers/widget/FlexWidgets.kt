package com.killersparrow1.clashtrackers.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.RoadToMax
import com.killersparrow1.clashtrackers.core.RoadToMaxCalc
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.data.MilestoneLog
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.data.StateRepository
import com.killersparrow1.clashtrackers.locale.LanguageManager
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The "flex" widgets: a hero strip (tap a hero for details), days to max, and the latest maxed items.
 * Like the Road to max widget they only change when a village export is synced, so nothing here ticks; [FlexWidgets.updateAll]
 * is called from the same refresh. The three share the Road to max setup screen (account and theme).
 */
abstract class FlexReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                FlexWidgets.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

class HeroWidgetReceiver : FlexReceiver()
class DaysWidgetReceiver : FlexReceiver()
class MilestonesWidgetReceiver : FlexReceiver()

/** A tap on a hero slot: shows that hero's details, or goes back to the hint when it is tapped again. */
class HeroTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val hero = intent.getIntExtra(EXTRA_HERO, 0)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || hero == 0) return
        val prefs = context.getSharedPreferences(FlexWidgets.PREFS, Context.MODE_PRIVATE)
        val key = "hero_$id"
        prefs.edit().apply { if (prefs.getInt(key, 0) == hero) remove(key) else putInt(key, hero) }.apply()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                FlexWidgets.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_HERO = "hero"
    }
}

object FlexWidgets {
    const val PREFS = "flex_widgets"
    private val GOLD = Color.parseColor("#FFC107")

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    /** Redraws every placed hero, days and milestones widget; returns how many there are. */
    suspend fun updateAll(context: Context): Int {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val hero = ids(app, HeroWidgetReceiver::class.java)
        val days = ids(app, DaysWidgetReceiver::class.java)
        val mile = ids(app, MilestonesWidgetReceiver::class.java)
        if (hero.isEmpty() && days.isEmpty() && mile.isEmpty()) return 0
        val repo = StateRepository(app)
        val accounts = repo.accounts.first()
        val fallback = repo.selectedAccountTag.first() ?: accounts.firstOrNull()?.tag
        val data = MaxDataStore.get(app)
        val renderer = WidgetRenderer(app)
        val names = Names.get(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nowSec = System.currentTimeMillis() / 1000

        suspend fun forEach(widgetIds: IntArray, draw: (id: Int, tag: String?, name: String?, road: RoadToMax?, builders: Int, theme: String) -> RemoteViews) {
            for (id in widgetIds) {
                val tag = repo.getWidgetAccount(id) ?: fallback
                val name = accounts.firstOrNull { it.tag == tag }?.displayName
                val export = tag?.let { repo.getAccountState(it).export }
                val builders = if (tag == null) 1 else
                    AppSettings(totalBuildersOverride = repo.getAccountSettings(tag).totalBuildersOverride).totalBuilders(export)
                val road = export?.let { RoadToMaxCalc.compute(data, it) }
                runCatching { manager.updateAppWidget(id, draw(id, tag, name, road, builders, repo.getWidgetTheme(id))) }
            }
        }

        forEach(hero) { id, _, name, road, _, theme -> heroViews(app, renderer, id, name, road, theme, prefs.getInt("hero_$id", 0), { x -> names.name(x) }) }
        forEach(days) { _, _, name, road, builders, theme -> daysViews(app, renderer, name, road, builders, theme) }
        forEach(mile) { _, tag, name, _, _, theme ->
            milestoneViews(app, renderer, name, tag?.let { MilestoneLog.recent(app, it, 3) }.orEmpty(), theme, nowSec)
        }
        return hero.size + days.size + mile.size
    }

    private fun duration(seconds: Long, dayUnit: String): String {
        val days = seconds / 86_400
        return if (days >= 1) String.format(Locale.US, "%,d %s", days, dayUnit) else Countdown.text(seconds, dayUnit)
    }

    private fun openApp(context: Context, request: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.killersparrow1.clashtrackers.MainActivity::class.java)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun syncTap(context: Context): PendingIntent {
        val intent = Intent(context, com.killersparrow1.clashtrackers.sync.SyncActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(com.killersparrow1.clashtrackers.sync.SyncActivity.EXTRA_WIDGET_TAP, true)
        return PendingIntent.getActivity(context, 12, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun heroViews(
        context: Context, renderer: WidgetRenderer, widgetId: Int, name: String?, road: RoadToMax?, theme: String, selected: Int,
        heroName: (Int) -> String,
    ): RemoteViews {
        val ctx = LanguageManager.wrap(context)
        val t = renderer.palette(theme)
        val rv = RemoteViews(context.packageName, R.layout.widget_hero)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setTextColor(R.id.who, t.text)
        rv.setTextColor(R.id.detail, t.sub)
        rv.setTextViewText(R.id.who, listOfNotNull(name, ctx.getString(R.string.hero_widget_name)).joinToString(" · "))
        rv.removeAllViews(R.id.slots)
        val heroes = road?.items?.filter { it.group == 'h' && it.levels.any { l -> l > 0 } }?.sortedBy { it.id }?.take(5).orEmpty()
        if (road == null || heroes.isEmpty()) {
            rv.setTextViewText(R.id.detail, ctx.getString(R.string.road_widget_empty))
            rv.setOnClickPendingIntent(R.id.panel, syncTap(context))
            return rv
        }
        val icons = WidgetIcons.get(context)
        val iconPx = (44 * context.resources.displayMetrics.density).toInt() * 2
        val data = MaxDataStore.get(context)
        val day = ctx.getString(R.string.time_day_short)
        for (hero in heroes) {
            val have = hero.levels.maxOrNull() ?: 0
            val slot = RemoteViews(context.packageName, R.layout.widget_hero_slot)
            icons.bitmapForLevel(hero.id, null, iconPx)?.let { slot.setImageViewBitmap(R.id.icon, it) }
            slot.setInt(R.id.slot, "setBackgroundResource", if (hero.id == selected) t.rowSlot else 0)
            if (hero.maxed) {
                slot.setTextViewText(R.id.lv, ctx.getString(R.string.hero_widget_max))
                slot.setTextColor(R.id.lv, GOLD)
            } else {
                slot.setTextViewText(R.id.lv, have.toString())
                slot.setTextColor(R.id.lv, t.text)
            }
            slot.setOnClickPendingIntent(R.id.slot, tapHero(context, widgetId, hero.id))
            rv.addView(R.id.slots, slot)
        }
        val chosen = heroes.firstOrNull { it.id == selected }
        if (chosen == null) {
            rv.setTextViewText(R.id.detail, ctx.getString(R.string.hero_widget_hint))
        } else {
            val have = chosen.levels.maxOrNull() ?: 0
            val left = data.items[chosen.id]?.steps?.let { steps -> (have until chosen.maxLevel).sumOf { steps[it].seconds } } ?: 0L
            rv.setTextViewText(
                R.id.detail,
                if (chosen.maxed) ctx.getString(R.string.hero_widget_detail_max, heroName(chosen.id), chosen.maxLevel)
                else ctx.getString(R.string.hero_widget_detail, heroName(chosen.id), have, chosen.maxLevel, duration(left, day)),
            )
        }
        rv.setOnClickPendingIntent(R.id.who, openApp(context, 13))
        return rv
    }

    private fun tapHero(context: Context, widgetId: Int, heroId: Int): PendingIntent {
        val intent = Intent(context, HeroTapReceiver::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(HeroTapReceiver.EXTRA_HERO, heroId)
        // One request code per widget and hero, so every slot keeps its own extras.
        return PendingIntent.getBroadcast(context, widgetId * 100_000 + (heroId % 100_000), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun daysViews(context: Context, renderer: WidgetRenderer, name: String?, road: RoadToMax?, builders: Int, theme: String): RemoteViews {
        val ctx = LanguageManager.wrap(context)
        val t = renderer.palette(theme)
        val rv = RemoteViews(context.packageName, R.layout.widget_days)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setTextColor(R.id.big, t.text)
        rv.setTextColor(R.id.caption, t.sub)
        if (road == null) {
            rv.setTextViewText(R.id.big, ctx.getString(R.string.days_widget_name))
            rv.setTextViewText(R.id.caption, ctx.getString(R.string.road_widget_empty))
            rv.setProgressBar(R.id.bar, 1000, 0, false)
            rv.setOnClickPendingIntent(R.id.panel, syncTap(context))
            return rv
        }
        val day = ctx.getString(R.string.time_day_short)
        val seconds = road.builderSeconds(builders)
        rv.setTextViewText(R.id.big, if (seconds <= 0L) ctx.getString(R.string.days_widget_maxed) else duration(seconds, day))
        rv.setTextViewText(R.id.caption, ctx.getString(R.string.days_widget_caption_lab, duration(road.lab.seconds, day)))
        rv.setProgressBar(R.id.bar, 1000, ((road.overallDone ?: 0.0) * 1000).toInt().coerceIn(0, 1000), false)
        rv.setOnClickPendingIntent(R.id.panel, openApp(context, 14))
        return rv
    }

    private fun milestoneViews(
        context: Context, renderer: WidgetRenderer, name: String?, entries: List<MilestoneLog.Entry>, theme: String, nowSec: Long,
    ): RemoteViews {
        val ctx = LanguageManager.wrap(context)
        val t = renderer.palette(theme)
        val rv = RemoteViews(context.packageName, R.layout.widget_milestones)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setTextColor(R.id.who, t.text)
        rv.setTextColor(R.id.empty, t.text)
        rv.setTextViewText(R.id.who, listOfNotNull(name, ctx.getString(R.string.milestones_widget_title)).joinToString(" · "))
        rv.removeAllViews(R.id.list)
        rv.setOnClickPendingIntent(R.id.panel, openApp(context, 15))
        if (entries.isEmpty()) {
            rv.setViewVisibility(R.id.list, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, ctx.getString(R.string.milestones_widget_empty))
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.list, View.VISIBLE)
        val icons = WidgetIcons.get(context)
        val iconPx = (26 * context.resources.displayMetrics.density).toInt() * 2
        for (e in entries) {
            val row = RemoteViews(context.packageName, R.layout.widget_milestone_row)
            row.setInt(R.id.row, "setBackgroundResource", t.rowSlot)
            icons.bitmapForLevel(e.id, e.level, iconPx)?.let { row.setImageViewBitmap(R.id.icon, it) } ?: row.setViewVisibility(R.id.icon, View.GONE)
            row.setTextViewText(R.id.title, ctx.getString(R.string.milestones_widget_row, e.name, e.level))
            row.setTextColor(R.id.title, t.text)
            val ageDays = ((nowSec - e.at) / 86_400).toInt().coerceAtLeast(0)
            row.setTextViewText(R.id.age, if (ageDays == 0) ctx.getString(R.string.milestones_widget_today) else ctx.getString(R.string.milestones_widget_days_ago, ageDays))
            row.setTextColor(R.id.age, t.sub)
            rv.addView(R.id.list, row)
        }
        return rv
    }
}
