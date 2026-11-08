package com.killersparrow1.clashtrackers.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.killersparrow1.clashtrackers.core.WidgetModel
import kotlinx.coroutines.runBlocking

/** Feeds the scrolling List widget: section headings and timer rows, rebuilt whenever the widget redraws. */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
            intent.getBooleanExtra(EXTRA_SMALL, false),
        )

    companion object {
        /** Intent extra: the widget is in its small size, so the list is one flat run of compact rows. */
        const val EXTRA_SMALL = "small"
    }

    private class Factory(private val context: Context, private val widgetId: Int, private val small: Boolean) : RemoteViewsFactory {
        private var model: WidgetModel? = null
        private var live = true
        private var tiles = false
        private var items: List<WidgetRenderer.ListItem> = emptyList()
        private lateinit var renderer: WidgetRenderer

        override fun onCreate() {
            renderer = WidgetRenderer(context)
        }

        override fun onDataSetChanged() {
            val built = runCatching { runBlocking { WidgetUpdater.modelFor(context, widgetId) } }.getOrNull()
            if (built == null) {
                items = emptyList()
                return
            }
            model = built.first
            live = built.second
            val t = renderer.themePalette(built.first.widgetTheme)
            tiles = runCatching {
                runBlocking { com.killersparrow1.clashtrackers.data.StateRepository(context).getWidgetStyle(widgetId) }
            }.getOrNull() == com.killersparrow1.clashtrackers.data.StateRepository.WIDGET_STYLE_TILES
            items = when {
                small -> renderer.smallItems(built.first)
                tiles -> renderer.tileItems(built.first, t)
                else -> renderer.listItems(built.first, t)
            }
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val m = model
            val item = items.getOrNull(position)
            if (m == null || item == null) return RemoteViews(context.packageName, com.killersparrow1.clashtrackers.R.layout.widget_list_header)
            val t = renderer.themePalette(m.widgetTheme)
            return when (item) {
                is WidgetRenderer.ListItem.Header -> renderer.headerView(item, m, t)
                is WidgetRenderer.ListItem.Timer -> renderer.timerView(item.row, m, live, t, compact = small)
                is WidgetRenderer.ListItem.Tiles -> renderer.tilesView(item.rows, m, live, t)
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 3
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}
