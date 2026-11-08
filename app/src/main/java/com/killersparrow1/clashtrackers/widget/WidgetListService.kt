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
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var model: WidgetModel? = null
        private var live = true
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
            items = renderer.listItems(built.first, renderer.themePalette(built.first.widgetTheme))
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
                is WidgetRenderer.ListItem.Timer -> renderer.timerView(item.row, m, live, t)
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 2
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}
