@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.RoadToMax
import com.killersparrow1.clashtrackers.core.RoadToMaxCalc
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.data.StateRepository
import com.killersparrow1.clashtrackers.locale.LanguageManager
import com.killersparrow1.clashtrackers.sync.SyncActivity
import com.killersparrow1.clashtrackers.ui.compact
import com.killersparrow1.clashtrackers.ui.theme.ClashTimerTheme
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The "Road to max" widget: builder, lab and pet time (and, when wide enough, Gold / Elixir / Dark Elixir) still needed
 * to max the current Town Hall. It follows the app's selected account and the widget theme. The numbers only change
 * when an export is synced, so [WidgetUpdater.updateAll] re-renders it and nothing ticks.
 */
class RoadWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    // Placed, resized or asked to update by the system: always redraw.
    private fun refresh(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                RoadWidgetUpdater.updateAll(context, force = true)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

object RoadWidgetUpdater {
    /** Width (dp) from which the resource column is added. */
    private const val WIDE_DP = 200
    private val lastDrawn = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, RoadWidgetReceiver::class.java))

    /** Re-renders every placed Road to max widget; returns how many there are. */
    suspend fun updateAll(context: Context, force: Boolean = false): Int {
        val app = context.applicationContext
        val ids = ids(app)
        if (ids.isEmpty()) return 0
        val repo = StateRepository(app)
        val manager = AppWidgetManager.getInstance(app)
        val accounts = repo.accounts.first()
        val fallback = repo.selectedAccountTag.first() ?: accounts.firstOrNull()?.tag
        val data = MaxDataStore.get(app)
        val renderer = WidgetRenderer(app)
        for (id in ids) {
            val tag = repo.getWidgetAccount(id) ?: fallback
            val export = tag?.let { repo.getAccountState(it).export }
            val road = export?.let { RoadToMaxCalc.compute(data, it) }
            val theme = repo.getWidgetTheme(id)
            val widthDp = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            // The countdown alarms call this often; only send the widget when something it shows has changed.
            val signature = java.util.Objects.hash(road, theme, widthDp >= WIDE_DP, tag, java.util.Locale.getDefault())
            if (!force && lastDrawn[id] == signature) continue
            runCatching { manager.updateAppWidget(id, render(app, renderer, road, theme, widthDp >= WIDE_DP)) }
                .onSuccess { lastDrawn[id] = signature }
        }
        return ids.size
    }

    private fun render(context: Context, renderer: WidgetRenderer, road: RoadToMax?, theme: String, wide: Boolean): RemoteViews {
        val ctx = LanguageManager.wrap(context)
        val t = renderer.palette(theme)
        val icons = WidgetIcons.get(context)
        val density = context.resources.displayMetrics.density
        val iconPx = (26 * density).toInt() * 2
        val rv = RemoteViews(context.packageName, R.layout.widget_road)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setTextColor(R.id.who, t.text)
        rv.setTextColor(R.id.empty, t.text)

        if (road == null) {
            rv.setTextViewText(R.id.who, ctx.getString(R.string.road_widget_title))
            rv.setViewVisibility(R.id.body, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, ctx.getString(R.string.road_widget_empty))
            rv.setOnClickPendingIntent(R.id.panel, syncIntent(context))
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.body, View.VISIBLE)
        rv.setTextViewText(R.id.who, ctx.getString(R.string.road_title, road.townHall))
        rv.setOnClickPendingIntent(R.id.panel, openAppIntent(context))

        fun row(label: String, value: String, building: Int?, drawable: Int?): RemoteViews {
            val r = RemoteViews(context.packageName, R.layout.widget_road_row)
            r.setInt(R.id.row, "setBackgroundResource", t.rowSlot)
            r.setTextViewText(R.id.label, label)
            r.setTextColor(R.id.label, t.sub)
            r.setTextViewText(R.id.value, value)
            r.setTextColor(R.id.value, t.text)
            val bmp = building?.let { icons.bitmapForLevel(it, null, iconPx) }
            when {
                bmp != null -> r.setImageViewBitmap(R.id.icon, bmp)
                drawable != null -> r.setImageViewResource(R.id.icon, drawable)
                else -> r.setViewVisibility(R.id.icon, View.GONE)
            }
            return r
        }

        val day = ctx.getString(R.string.time_day_short)
        val all = road.builderWork + road.lab + road.pets
        rv.removeAllViews(R.id.col_time)
        rv.addView(R.id.col_time, row(ctx.getString(R.string.road_widget_builders), duration(road.builderWork.seconds, day), 1000015, null))
        rv.addView(R.id.col_time, row(ctx.getString(R.string.road_lab), duration(road.lab.seconds, day), 1000007, null))
        rv.addView(R.id.col_time, row(ctx.getString(R.string.road_pets), duration(road.pets.seconds, day), 1000068, null))

        rv.removeAllViews(R.id.col_res)
        if (wide) {
            rv.setViewVisibility(R.id.col_res, View.VISIBLE)
            rv.addView(R.id.col_res, row(ctx.getString(R.string.resource_gold), compact(all.gold), null, R.drawable.road_gold))
            rv.addView(R.id.col_res, row(ctx.getString(R.string.resource_elixir), compact(all.elixir), null, R.drawable.road_elixir))
            rv.addView(R.id.col_res, row(ctx.getString(R.string.resource_dark_elixir), compact(all.darkElixir), null, R.drawable.road_dark_elixir))
        } else {
            rv.setViewVisibility(R.id.col_res, View.GONE)
        }
        return rv
    }

    private fun duration(seconds: Long, dayUnit: String): String {
        val days = seconds / 86_400
        return if (days >= 1) String.format(Locale.US, "%,d %s", days, dayUnit) else Countdown.text(seconds, dayUnit)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.killersparrow1.clashtrackers.MainActivity::class.java)
        return PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun syncIntent(context: Context): PendingIntent {
        val intent = Intent(context, SyncActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        return PendingIntent.getActivity(context, 11, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Setup screen for the Road to max widget: only the theme (the account follows the one selected in the app). */
class RoadWidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED)
        val scope = CoroutineScope(Dispatchers.Main)
        setContent {
            ClashTimerTheme {
                Surface(Modifier.fillMaxSize()) {
                    val themes = listOf(
                        "material_you" to stringResource(R.string.widget_theme_material_you),
                        "clash" to stringResource(R.string.widget_theme_clash),
                        "dark" to stringResource(R.string.widget_theme_dark),
                        "black" to stringResource(R.string.widget_theme_black),
                        "light" to stringResource(R.string.widget_theme_light),
                    )
                    var theme by rememberSaveable { mutableStateOf<String?>(null) }
                    androidx.compose.runtime.LaunchedEffect(appWidgetId) {
                        theme = StateRepository(this@RoadWidgetConfigActivity).getWidgetTheme(appWidgetId)
                    }
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.road_widget_name), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.widget_theme), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            themes.forEach { (key, label) ->
                                FilterChip(selected = theme == key, onClick = { theme = key }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { finish() }) { Text(stringResource(R.string.action_cancel)) }
                            Button(onClick = {
                                scope.launch {
                                    StateRepository(this@RoadWidgetConfigActivity).setWidgetTheme(appWidgetId, theme ?: "material_you")
                                    RoadWidgetUpdater.updateAll(this@RoadWidgetConfigActivity, force = true)
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                    finish()
                                }
                            }) { Text(stringResource(R.string.action_save)) }
                        }
                    }
                }
            }
        }
    }
}
