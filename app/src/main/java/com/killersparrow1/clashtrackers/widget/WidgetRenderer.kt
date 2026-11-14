package com.killersparrow1.clashtrackers.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.BoostPill
import com.killersparrow1.clashtrackers.core.GameIds
import com.killersparrow1.clashtrackers.sync.ClockTowerTracking
import com.killersparrow1.clashtrackers.core.Countdown
import com.killersparrow1.clashtrackers.core.Finish
import com.killersparrow1.clashtrackers.core.FooterTone
import com.killersparrow1.clashtrackers.core.RowButton
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.WidgetActions
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.core.WidgetRow
import com.killersparrow1.clashtrackers.locale.LanguageManager
import com.killersparrow1.clashtrackers.sync.SyncActivity
import kotlin.math.roundToInt

/** Maps a [WidgetModel] onto `widget_root.xml` + one `widget_row*.xml` per row. */
class WidgetRenderer(private val context: Context) {

    private val pkg = context.packageName
    private val icons = WidgetIcons.get(context)
    private val density = context.resources.displayMetrics.density
    private val lctx get() = LanguageManager.wrap(context)

    // Read from resources so Android 12+ can swap in Material You (wallpaper) colours; see values-v31/widget_colors.xml.
    private val COLOR_WHITE = context.getColor(R.color.w_white)
    private val COLOR_SUB = context.getColor(R.color.w_sub)
    private val COLOR_GOLD = context.getColor(R.color.w_gold)
    private val COLOR_GOBLIN = context.getColor(R.color.w_goblin)
    private val COLOR_IDLE = context.getColor(R.color.w_idle)
    private val COLOR_ERROR = context.getColor(R.color.w_error)
    private val COLOR_SYNC_TEXT = context.getColor(R.color.w_sync_text)

    data class Palette(
        @param:DrawableRes val panel: Int,
        @param:DrawableRes val rowSlot: Int,
        @param:DrawableRes val rowDone: Int,
        @param:DrawableRes val pillOn: Int,
        @param:DrawableRes val pillOff: Int,
        @param:DrawableRes val iconSlot: Int,
        @param:DrawableRes val syncButton: Int,
        @param:DrawableRes val plusButton: Int,
        @param:DrawableRes val helperButton: Int,
        val text: Int,
        val sub: Int,
        val gold: Int,
        val goblin: Int,
        val idle: Int,
        val error: Int,
        val syncText: Int,
        /** Which of the row layout's progress bars belongs to this theme. */
        val fillId: Int = R.id.row_fill,
    )

    private val ROW_FILLS = intArrayOf(R.id.row_fill, R.id.row_fill_clash, R.id.row_fill_dark, R.id.row_fill_light)

    /** Shows only the progress bar that belongs to [theme] (Material You keeps the system accent) and returns its id. */
    private fun showFill(v: RemoteViews, id: Int): Int {
        ROW_FILLS.forEach { v.setViewVisibility(it, if (it == id) View.VISIBLE else View.GONE) }
        return id
    }

    internal fun palette(theme: String): Palette = basePalette(theme).copy(
        fillId = when (theme) {
            "clash" -> R.id.row_fill_clash
            "dark" -> R.id.row_fill_dark
            "light" -> R.id.row_fill_light
            else -> R.id.row_fill
        },
    )

    private fun basePalette(theme: String): Palette = when (theme) {
        "clash" -> Palette(
            R.drawable.widget_panel_clash, R.drawable.row_slot_clash, R.drawable.row_done_clash,
            R.drawable.pill_on_clash, R.drawable.pill_off_clash, R.drawable.icon_slot_clash,
            R.drawable.btn_sync_clash, R.drawable.btn_plus_clash, R.drawable.btn_helper_clash,
            0xFFFFFFFF.toInt(), 0xFFE9DCC8.toInt(), 0xFFFFD24A.toInt(), 0xFF6FD36A.toInt(),
            0xFFFFB35C.toInt(), 0xFFFF7A7A.toInt(), 0xFF5A3500.toInt(),
        )
        "dark" -> Palette(
            R.drawable.widget_panel_dark, R.drawable.row_slot_dark, R.drawable.row_done_dark,
            R.drawable.pill_on_dark, R.drawable.pill_off_dark, R.drawable.icon_slot_dark,
            R.drawable.btn_sync_dark, R.drawable.btn_plus_dark, R.drawable.btn_helper_dark,
            0xFFECEEF2.toInt(), 0xFFAEB4BF.toInt(), 0xFFFFD24A.toInt(), 0xFF7BE07A.toInt(),
            0xFFFFB35C.toInt(), 0xFFFF8A8A.toInt(), 0xFF101418.toInt(),
        )
        // Material You colours (accent bar, buttons, text) on a pure black panel.
        "black" -> basePalette("material_you").copy(
            panel = R.drawable.widget_panel_black,
            rowSlot = R.drawable.row_slot_black,
            rowDone = R.drawable.row_done_black,
            pillOff = R.drawable.pill_off_black,
        )
        "light" -> Palette(
            R.drawable.widget_panel_light, R.drawable.row_slot_light, R.drawable.row_done_light,
            R.drawable.pill_on_light, R.drawable.pill_off_light, R.drawable.icon_slot_light,
            R.drawable.btn_sync_light, R.drawable.btn_plus_light, R.drawable.btn_helper_light,
            0xFF1B1D21.toInt(), 0xFF565C66.toInt(), 0xFF8A5A10.toInt(), 0xFF1F6D2A.toInt(),
            0xFFA34E00.toInt(), 0xFFB3261E.toInt(), 0xFF3A2500.toInt(),
        )
        else -> Palette(
            R.drawable.widget_panel, R.drawable.row_slot, R.drawable.row_done,
            R.drawable.pill_on, R.drawable.pill_off, R.drawable.icon_slot,
            R.drawable.btn_sync, R.drawable.btn_plus, R.drawable.btn_helper,
            COLOR_WHITE, COLOR_SUB, COLOR_GOLD, COLOR_GOBLIN, COLOR_IDLE, COLOR_ERROR, COLOR_SYNC_TEXT,
        )
    }

    /** Android 16+: put the rows inside the widget itself instead of having the launcher call WidgetListService. */
    var embedItems: Boolean = true
    private var itemStyle: String? = null

    /**
     * Connects the scrolling list. Android 16 and newer (API 36+) build the rows here and sends them with the widget
     * (RemoteCollectionItems): CT1.2, which had no list service, drew correct icons on Android 17 while the
     * service-backed list showed wrong ones. Everything older keeps the service. If the rows are too big to send,
     * the caller turns [embedItems] off and the service is used.
     */
    private fun attachList(rv: RemoteViews, appWidgetId: Int, small: Boolean, cols: Int, model: WidgetModel, live: Boolean) {
        if (embedItems && Build.VERSION.SDK_INT >= 36) {
            val items = runCatching {
                val factory = WidgetListService.Factory(context, appWidgetId, small, cols, WidgetListService.Preset(model, live, itemStyle))
                factory.onCreate()
                factory.onDataSetChanged()
                val b = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(8).setHasStableIds(false)
                for (i in 0 until factory.getCount()) b.addItem(i.toLong(), factory.getViewAt(i))
                b.build()
            }.getOrNull()
            if (items != null) {
                rv.setRemoteAdapter(R.id.list, items)
                rv.setPendingIntentTemplate(R.id.list, tapTemplate())
                return
            }
        }
        val adapter = Intent(context, WidgetListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(WidgetListService.EXTRA_COLS, cols)
        if (small) adapter.putExtra(WidgetListService.EXTRA_SMALL, true)
        adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
        rv.setRemoteAdapter(R.id.list, adapter)
        rv.setPendingIntentTemplate(R.id.list, tapTemplate())
    }

    fun render(model: WidgetModel, liveCountdown: Boolean, options: Bundle?, style: String = "list", appWidgetId: Int = 0): RemoteViews {
        itemStyle = style
        val heightDp = reportedHeightDp(options) ?: 320
        val isSmall = heightDp < 200
        if (style == com.killersparrow1.clashtrackers.data.StateRepository.WIDGET_STYLE_TILES) {
            return renderLarge(model, liveCountdown, options, appWidgetId)
        }
        if (style == com.killersparrow1.clashtrackers.data.StateRepository.WIDGET_STYLE_MINIMAL) {
            return renderMinimal(model, appWidgetId, liveCountdown)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val smallSize = android.util.SizeF(250f, 130f)
            val largeSize = android.util.SizeF(250f, 320f)
            val smallRv = renderSmall(model, liveCountdown, options, appWidgetId)
            val largeRv = renderLarge(model, liveCountdown, options, appWidgetId)
            return RemoteViews(mapOf(smallSize to smallRv, largeSize to largeRv))
        } else {
            return if (isSmall) renderSmall(model, liveCountdown, options, appWidgetId) else renderLarge(model, liveCountdown, options, appWidgetId)
        }
    }

    private fun renderLarge(model: WidgetModel, liveCountdown: Boolean, options: Bundle?, appWidgetId: Int): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root_scroll)
        val sync = syncIntent()
        val t = palette(model.widgetTheme)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setInt(R.id.sync_button, "setBackgroundResource", t.syncButton)
        rv.setOnClickPendingIntent(R.id.header, sync)
        rv.setOnClickPendingIntent(R.id.sync_button, sync)
        rv.setTextViewText(R.id.who, title(model, t))
        rv.setTextColor(R.id.who, t.text)
        rv.setTextViewText(R.id.lbl_sync, lctx.getString(R.string.widget_sync))
        rv.setTextColor(R.id.lbl_sync, t.syncText)

        if (!model.hasData) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, sync)
            rv.setTextViewText(R.id.empty_text, if (model.syncFailed) model.text.syncFailed else model.text.noData)
            rv.setTextColor(R.id.empty_text, t.text)
            rv.setTextViewText(R.id.lbl_no_data_hint, lctx.getString(R.string.widget_no_data_hint))
            rv.setTextColor(R.id.lbl_no_data_hint, t.sub)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)

        // The sections and rows live in a scrolling list filled by WidgetListService (see listItems/headerView/rowView).
        attachList(rv, appWidgetId, small = false, cols = tileColumns(options), model = model, live = liveCountdown)

        rv.setTextViewText(R.id.foot_left, footer(model, t))
        rv.setTextColor(R.id.foot_left, t.sub)
        rv.setTextViewText(R.id.foot_right, model.footerRight)
        rv.setTextColor(R.id.foot_right, t.sub)
        return rv
    }

    /** Minimal (Nothing-inspired) style: black card, white dot-matrix type, one red accent. Ignores the colour theme. */
    private fun renderMinimal(model: WidgetModel, appWidgetId: Int, liveCountdown: Boolean): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root_minimal)
        val sync = syncIntent()
        rv.setOnClickPendingIntent(R.id.header, sync)
        rv.setOnClickPendingIntent(R.id.sync_button, sync)
        rv.setTextViewText(R.id.who, title(model, palette(model.widgetTheme)).toString().uppercase())
        rv.setTextViewText(R.id.lbl_sync, lctx.getString(R.string.widget_sync).uppercase())
        if (!model.hasData) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, sync)
            rv.setTextViewText(R.id.empty_text, if (model.syncFailed) model.text.syncFailed else model.text.noData)
            rv.setTextViewText(R.id.lbl_no_data_hint, lctx.getString(R.string.widget_no_data_hint))
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)
        attachList(rv, appWidgetId, small = false, cols = TILES_PER_ROW, model = model, live = liveCountdown)
        rv.setTextViewText(R.id.foot_left, footer(model, palette(model.widgetTheme)).toString().uppercase())
        rv.setTextViewText(R.id.foot_right, model.footerRight.uppercase())
        return rv
    }

    /** Minimal: a section label plus its potion / Bite chips. */
    fun minHeaderView(h: ListItem.Header, model: WidgetModel): RemoteViews {
        val v = RemoteViews(pkg, R.layout.widget_min_header)
        v.setTextViewText(R.id.mh_label, h.title.toString().uppercase())
        for (id in listOf(R.id.mp_bite, R.id.mp_soup, R.id.mp_bpot, R.id.mp_rpot, R.id.mp_ppot, R.id.mp_ct)) v.setViewVisibility(id, View.GONE)
        for (id in listOf(R.id.mp_bite_undo, R.id.mp_soup_undo, R.id.mp_bpot_undo, R.id.mp_rpot_undo, R.id.mp_ppot_undo)) v.setViewVisibility(id, View.GONE)
        for (slot in h.pills) {
            if (slot == PILL_CLOCKTOWER) {
                val ct = model.clockTower ?: continue
                v.setTextViewText(R.id.mp_ct, "CT ${ct.text}".uppercase())
                v.setTextColor(R.id.mp_ct, if (ct.highlighted) 0xFFD71921.toInt() else 0xFFFFFFFF.toInt())
                v.setInt(R.id.mp_ct, "setBackgroundResource", if (ct.highlighted) R.drawable.minimal_chip_on else R.drawable.minimal_chip)
                v.setViewVisibility(R.id.mp_ct, View.VISIBLE)
                continue
            }
            val (box, undo, pill) = when (slot) {
                PILL_BITE -> Triple(R.id.mp_bite, R.id.mp_bite_undo, model.bite)
                PILL_SOUP -> Triple(R.id.mp_soup, R.id.mp_soup_undo, model.soup)
                PILL_BUILDER -> Triple(R.id.mp_bpot, R.id.mp_bpot_undo, model.builderPotion)
                PILL_RESEARCH -> Triple(R.id.mp_rpot, R.id.mp_rpot_undo, model.researchPotion)
                else -> Triple(R.id.mp_ppot, R.id.mp_ppot_undo, model.petPotion)
            }
            val label = if (slot == PILL_BITE || slot == PILL_SOUP) pill.text else "POT ${pill.text}"
            v.setTextViewText(box, label.uppercase())
            v.setTextColor(box, if (pill.active) 0xFFD71921.toInt() else 0xFFFFFFFF.toInt())
            v.setInt(box, "setBackgroundResource", if (pill.active) R.drawable.minimal_chip_on else R.drawable.minimal_chip)
            v.setViewVisibility(box, View.VISIBLE)
            tap(v, box, WidgetActions.ADD_BOOST, pill.type.name, model.accountTag, true)
            if (pill.canUndo) {
                v.setViewVisibility(undo, View.VISIBLE)
                tap(v, undo, WidgetActions.UNDO_BOOST, pill.type.name, model.accountTag, true)
            }
        }
        return v
    }

    /** Minimal: one timer as a line of text with a thin progress bar. */
    fun minRowView(source: WidgetRow, model: WidgetModel, live: Boolean): RemoteViews {
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val row = if (source.kind == RowKind.RUNNING && source.finishAt != null && Finish.isDone(source.finishAt, nowMs / 1000)) {
            source.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            source
        }
        val v = RemoteViews(pkg, R.layout.widget_min_row)
        // Reused list rows keep old state, so set every optional piece each time.
        v.setViewVisibility(R.id.mr_goblin, if (row.goblin) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.mr_name, row.name.uppercase())
        val level = row.levelText?.takeIf { row.kind == RowKind.RUNNING }
        v.setTextViewText(R.id.mr_level, level?.let { (if (row.showArrow) "> " else "") + it.uppercase() } ?: "")
        v.setViewVisibility(R.id.mr_level, if (level != null) View.VISIBLE else View.GONE)
        val running = row.kind == RowKind.RUNNING
        v.setProgressBar(R.id.mr_bar, 1000, if (running) (row.progress * 1000).roundToInt() else if (row.kind == RowKind.COMPLETED) 1000 else 0, false)
        val segment = if (running && live) row.finishAt?.let { Countdown.segment(it, nowMs, row.text.day) } else null
        if (segment != null) {
            v.setViewVisibility(R.id.mr_chrono, View.VISIBLE)
            v.setViewVisibility(R.id.mr_text, View.GONE)
            v.setChronometer(R.id.mr_chrono, elapsed + segment.chronometerMs, segment.format, true)
            v.setChronometerCountDown(R.id.mr_chrono, true)
        } else {
            v.setViewVisibility(R.id.mr_chrono, View.GONE)
            v.setViewVisibility(R.id.mr_text, View.VISIBLE)
            v.setTextViewText(R.id.mr_text, row.staticStatus.uppercase())
        }
        if (row.button != RowButton.NONE && row.jobKey != null) {
            v.setViewVisibility(R.id.mr_btn, View.VISIBLE)
            val link = row.button == RowButton.LINK
            v.setTextViewText(R.id.mr_btn, if (link) "+" else "H")
            v.setTextColor(R.id.mr_btn, if (link) 0xFFD71921.toInt() else 0xFFFFFFFF.toInt())
            tap(v, R.id.mr_btn, WidgetActions.HELPER, row.jobKey, model.accountTag, true)
        } else {
            v.setViewVisibility(R.id.mr_btn, View.INVISIBLE)
        }
        return v
    }

    private fun renderSmall(model: WidgetModel, liveCountdown: Boolean, options: Bundle?, appWidgetId: Int): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root_small)
        val sync = syncIntent()
        val t = palette(model.widgetTheme)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setInt(R.id.sync_button, "setBackgroundResource", t.syncButton)
        rv.setOnClickPendingIntent(R.id.header, sync)
        rv.setOnClickPendingIntent(R.id.sync_button, sync)
        rv.setTextViewText(R.id.who, title(model, t))
        rv.setTextColor(R.id.who, t.text)

        if (!model.hasData) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, sync)
            rv.setTextViewText(R.id.empty_text, if (model.syncFailed) model.text.syncFailed else model.text.noData)
            rv.setTextColor(R.id.empty_text, t.text)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)

        // Every running timer, soonest first, in a scrolling list (WidgetListService, "small" mode).
        attachList(rv, appWidgetId, small = true, cols = TILES_PER_ROW, model = model, live = liveCountdown)
        return rv
    }

    private fun tile(model: WidgetRow, live: Boolean, nowMs: Long, elapsed: Long, accountTag: String, t: Palette): RemoteViews {
        val row = if (model.kind == RowKind.RUNNING && model.finishAt != null && Finish.isDone(model.finishAt, nowMs / 1000)) {
            model.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            model
        }
        val v = RemoteViews(pkg, R.layout.widget_tile)
        v.setInt(R.id.tile, "setBackgroundResource", t.rowSlot)
        val icon = row.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, row.iconLevel, (34 * density).roundToInt()) }
        if (icon != null) {
            v.setImageViewBitmap(R.id.tile_icon, icon)
            v.setViewVisibility(R.id.tile_icon, View.VISIBLE)
            v.setViewVisibility(R.id.tile_initials, View.GONE)
        } else {
            v.setTextViewText(R.id.tile_initials, row.initials)
            v.setTextColor(R.id.tile_initials, t.sub)
        }
        if (row.goblin) {
            icons.bitmap("goblin", (20 * density).roundToInt())?.let {
                v.setImageViewBitmap(R.id.tile_goblin, it)
                v.setViewVisibility(R.id.tile_goblin, View.VISIBLE)
            }
        }
        if (row.button != RowButton.NONE && row.jobKey != null) {
            v.setViewVisibility(R.id.tile_btn, View.VISIBLE)
            tap(v, R.id.tile_btn, WidgetActions.HELPER, row.jobKey, accountTag, true)
            when (row.button) {
                RowButton.LINK -> v.setInt(R.id.tile_btn, "setBackgroundResource", t.plusButton)
                else -> {
                    v.setInt(R.id.tile_btn, "setBackgroundResource", t.helperButton)
                    val name = if (row.button == RowButton.APPRENTICE) "apprentice" else "lab_assistant"
                    val badge = icons.bitmap(name, (20 * density).roundToInt())
                    if (badge != null) {
                        v.setImageViewBitmap(R.id.tile_btn_icon, badge)
                        v.setViewVisibility(R.id.tile_btn_icon, View.VISIBLE)
                        v.setViewVisibility(R.id.tile_btn_text, View.GONE)
                    } else {
                        v.setTextViewText(R.id.tile_btn_text, "H")
                        v.setTextColor(R.id.tile_btn_text, t.syncText)
                        v.setTextViewTextSize(R.id.tile_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, 10f)
                    }
                }
            }
        }
        v.setTextViewText(R.id.tile_name, row.name)
        v.setTextColor(R.id.tile_name, t.text)
        val level = row.levelText?.takeIf { row.kind == RowKind.RUNNING }
        if (level != null) {
            v.setTextViewText(R.id.tile_level, (if (row.showArrow) "\u2192 " else "") + level)
            v.setTextColor(R.id.tile_level, t.sub)
        } else {
            v.setViewVisibility(R.id.tile_level, View.GONE)
        }
        v.setTextColor(R.id.tile_chrono, t.gold)
        v.setTextColor(R.id.tile_text, t.gold)

        val segment = if (row.kind == RowKind.RUNNING && live) row.finishAt?.let { Countdown.segment(it, nowMs, row.text.day) } else null
        if (row.kind == RowKind.RUNNING) {
            v.setProgressBar(R.id.tile_ring, 1000, (row.progress * 1000).roundToInt(), false)
        } else {
            v.setProgressBar(R.id.tile_ring, 1000, 1000, false)
        }
        if (segment != null) {
            v.setChronometer(R.id.tile_chrono, elapsed + segment.chronometerMs, segment.format, true)
            v.setChronometerCountDown(R.id.tile_chrono, true)
        } else {
            v.setViewVisibility(R.id.tile_chrono, View.GONE)
            v.setViewVisibility(R.id.tile_text, View.VISIBLE)
            v.setTextViewText(R.id.tile_text, row.staticStatus)
        }
        return v
    }

    private fun row(model: WidgetRow, compact: Boolean, live: Boolean, nowMs: Long, elapsed: Long, accountTag: String, t: Palette, inList: Boolean = false): RemoteViews {
        // The model already marks a row done Finish.GRACE_SEC early. This also covers a model built a
        // moment before this render, so a chronometer is never started at or past its finish.
        val row = if (model.kind == RowKind.RUNNING && model.finishAt != null && Finish.isDone(model.finishAt, nowMs / 1000)) {
            model.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            model
        }
        val v = RemoteViews(pkg, if (compact) R.layout.widget_row_compact else R.layout.widget_row)
        val fillId = showFill(v, t.fillId)
        val iconPx = ((if (compact) 20 else 24) * density).roundToInt()
        if (inList) {
            // A scrolling list reuses row views, so put every optional piece back to the layout's default first.
            v.setViewVisibility(R.id.row_icon, View.GONE)
            v.setViewVisibility(R.id.row_initials, View.VISIBLE)
            v.setViewVisibility(R.id.row_goblin, View.GONE)
            v.setViewVisibility(R.id.row_goblin_icon, View.GONE)
            v.setViewVisibility(R.id.row_level, View.VISIBLE)
            v.setViewVisibility(R.id.row_clock, View.VISIBLE)
            v.setViewVisibility(R.id.row_chrono, View.VISIBLE)
            v.setViewVisibility(R.id.row_text, View.GONE)
            v.setViewVisibility(R.id.row_check, View.GONE)
            v.setViewVisibility(R.id.row_btn, View.VISIBLE)
            v.setViewVisibility(R.id.row_btn_icon, View.GONE)
            v.setViewVisibility(R.id.row_btn_text, View.VISIBLE)
            v.setTextViewText(R.id.row_btn_text, "+")
            v.setTextColor(R.id.row_btn_text, 0xFFFFFFFF.toInt())
            v.setTextViewTextSize(R.id.row_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, 15f)
            v.setInt(R.id.row_btn, "setBackgroundResource", t.plusButton)
        }
        v.setInt(R.id.row, "setBackgroundResource", if (row.kind == RowKind.RUNNING) t.rowSlot else t.rowDone)
        v.setInt(R.id.row_icon_slot, "setBackgroundResource", t.iconSlot)

        val icon = row.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, row.iconLevel, iconPx) }
        if (icon != null) {
            v.setImageViewBitmap(R.id.row_icon, icon)
            v.setViewVisibility(R.id.row_icon, View.VISIBLE)
            v.setViewVisibility(R.id.row_initials, View.GONE)
        } else {
            v.setTextViewText(R.id.row_initials, row.initials)
            v.setTextColor(R.id.row_initials, t.sub)
        }

        if (row.goblin) {
            v.setViewVisibility(R.id.row_goblin, View.VISIBLE)
            icons.bitmap("goblin", (11 * density).roundToInt())?.let {
                v.setImageViewBitmap(R.id.row_goblin_icon, it)
                v.setViewVisibility(R.id.row_goblin_icon, View.VISIBLE)
            }
        }
        v.setTextViewText(R.id.row_name, row.name)
        v.setTextColor(R.id.row_name, t.text)
        // Finished rows show just the name (e.g. "Inferno Tower   Completed ✓").
        val level = row.levelText?.takeIf { row.kind == RowKind.RUNNING }
        v.setViewVisibility(R.id.row_arrow, if (level != null && row.showArrow) View.VISIBLE else View.GONE)
        v.setInt(R.id.row_arrow, "setColorFilter", t.sub)
        if (level != null) {
            v.setTextViewText(R.id.row_level, level)
            v.setTextColor(R.id.row_level, t.sub)
        } else {
            v.setViewVisibility(R.id.row_level, View.GONE)
        }
        v.setTextColor(R.id.row_chrono, t.text)
        v.setTextColor(R.id.row_text, t.text)
        v.setInt(R.id.row_check, "setColorFilter", t.goblin)

        when (row.kind) {
            RowKind.RUNNING -> {
                v.setProgressBar(fillId, 1000, (row.progress * 1000).roundToInt(), false)
                icons.bitmap("clock", (12 * density).roundToInt())?.let { v.setImageViewBitmap(R.id.row_clock, it) }
                val segment = if (live) row.finishAt?.let { Countdown.segment(it, nowMs, model.text.day) } else null
                if (segment != null) {
                    v.setChronometer(R.id.row_chrono, elapsed + segment.chronometerMs, segment.format, true)
                    v.setChronometerCountDown(R.id.row_chrono, true)
                } else {
                    v.setViewVisibility(R.id.row_chrono, View.GONE)
                    v.setViewVisibility(R.id.row_text, View.VISIBLE)
                    v.setTextViewText(R.id.row_text, row.staticStatus)
                }
            }
            RowKind.COMPLETED, RowKind.IDLE -> {
                ROW_FILLS.forEach { v.setViewVisibility(it, View.GONE) }
                v.setViewVisibility(R.id.row_clock, View.GONE)
                v.setViewVisibility(R.id.row_chrono, View.GONE)
                v.setViewVisibility(R.id.row_text, View.VISIBLE)
                v.setTextViewText(R.id.row_text, row.staticStatus)
                if (row.kind == RowKind.COMPLETED) v.setViewVisibility(R.id.row_check, View.VISIBLE)
            }
        }

        if (row.button != RowButton.NONE && row.jobKey != null) {
            tap(v, R.id.row_btn, WidgetActions.HELPER, row.jobKey, accountTag, inList)
        }
        when (row.button) {
            RowButton.NONE -> v.setViewVisibility(R.id.row_btn, View.INVISIBLE)
            RowButton.LINK -> v.setInt(R.id.row_btn, "setBackgroundResource", t.plusButton)
            RowButton.APPRENTICE, RowButton.LAB_ASSISTANT -> {
                v.setInt(R.id.row_btn, "setBackgroundResource", t.helperButton)
                val name = if (row.button == RowButton.APPRENTICE) "apprentice" else "lab_assistant"
                val badge = icons.bitmap(name, ((if (compact) 19 else 22) * density).roundToInt())
                if (badge != null) {
                    v.setImageViewBitmap(R.id.row_btn_icon, badge)
                    v.setViewVisibility(R.id.row_btn_icon, View.VISIBLE)
                    v.setViewVisibility(R.id.row_btn_text, View.GONE)
                } else {
                    v.setTextViewText(R.id.row_btn_text, "H")
                    v.setTextColor(R.id.row_btn_text, t.syncText)
                    v.setTextViewTextSize(R.id.row_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, 10f)
                }
            }
        }
        return v
    }

    private class PillIds(
        @param:IdRes val box: Int, @param:IdRes val add: Int, @param:IdRes val undo: Int,
        @param:IdRes val icon: Int, @param:IdRes val text: Int,
    )

    private fun pill(rv: RemoteViews, ids: PillIds, pill: BoostPill, asset: String, @DrawableRes fallback: Int, accountTag: String, t: Palette, inList: Boolean = false) {
        val bmp = icons.bitmap(asset, (14 * density).roundToInt())
        if (bmp != null) rv.setImageViewBitmap(ids.icon, bmp) else rv.setImageViewResource(ids.icon, fallback)
        rv.setTextViewText(ids.text, pill.text)
        rv.setTextColor(ids.text, if (pill.active) t.gold else t.text)
        rv.setInt(ids.box, "setBackgroundResource", if (pill.active || pill.canUndo) t.pillOn else t.pillOff)
        tap(rv, ids.add, WidgetActions.ADD_BOOST, pill.type.name, accountTag, inList)
        rv.setViewVisibility(ids.undo, if (pill.canUndo) View.VISIBLE else View.GONE)
        if (pill.canUndo) tap(rv, ids.undo, WidgetActions.UNDO_BOOST, pill.type.name, accountTag, inList)
    }

    private fun tapUri(action: String, arg: String, accountTag: String): Uri =
        Uri.Builder().scheme("clashtracker").authority("tap").appendPath(action).appendPath(arg)
            .appendQueryParameter("account", accountTag).build()

    /**
     * Broadcast to [ClashWidgetReceiver] for a button tap, handled in the background (no activity, no popup).
     * The URI makes every action + argument a distinct PendingIntent.
     */
    private fun tapIntent(action: String, arg: String, accountTag: String): PendingIntent {
        val intent = Intent(context, ClashWidgetReceiver::class.java)
            .setAction(ClashWidgetReceiver.ACTION_TAP)
            .setData(tapUri(action, arg, accountTag))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Buttons inside a scrolling list can't carry their own PendingIntent: they fill in this template's URI instead. */
    private fun tapTemplate(): PendingIntent {
        val intent = Intent(context, ClashWidgetReceiver::class.java).setAction(ClashWidgetReceiver.ACTION_TAP)
        return PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun tap(v: RemoteViews, @IdRes id: Int, action: String, arg: String, accountTag: String, inList: Boolean) {
        if (inList) v.setOnClickFillInIntent(id, Intent().setData(tapUri(action, arg, accountTag)))
        else v.setOnClickPendingIntent(id, tapIntent(action, arg, accountTag))
    }

    /** One entry of the scrolling list: a section heading (with its potion pills) or a timer row. */
    sealed class ListItem {
        class Header(val title: CharSequence, val pills: List<Int>) : ListItem()
        class Timer(val row: WidgetRow) : ListItem()
        /** Up to [TILES_PER_ROW] tiles side by side (Tiles style). */
        class Tiles(val rows: List<WidgetRow>) : ListItem()
    }

    /** Pill slots a section heading can show, in the order [headerView] knows them. */
    private val pillSlots = listOf(PILL_BITE, PILL_BUILDER, PILL_RESEARCH, PILL_PET, PILL_SOUP, PILL_CLOCKTOWER)

    fun listItems(model: WidgetModel, t: Palette): List<ListItem> {
        val items = mutableListOf<ListItem>()
        fun section(title: CharSequence, pills: List<Int>, rows: List<WidgetRow>) {
            items += ListItem.Header(title, pills)
            rows.take(MAX_BUILDER_ROWS).forEach { items += ListItem.Timer(it) }
        }
        section(buildersLabel(model, t), listOf(PILL_BITE, PILL_BUILDER), model.builderRows)
        section(lctx.getString(R.string.widget_lab), listOf(PILL_SOUP, PILL_RESEARCH), model.labRows)
        section(lctx.getString(R.string.widget_pets), listOf(PILL_PET), listOf(model.petRow))
        val ctPill = if (model.clockTower != null && ClockTowerTracking.enabled(context)) listOf(PILL_CLOCKTOWER) else emptyList()
        if (model.showBuilderBase || ctPill.isNotEmpty()) {
            section(lctx.getString(R.string.widget_bb), ctPill, model.bbRows + listOfNotNull(model.starLabRow))
        }
        return items
    }

    /** Tiles style: the same section headings, then each section's busy or just-finished timers, 3 tiles per list item. */
    fun tileItems(model: WidgetModel, t: Palette, cols: Int = TILES_PER_ROW): List<ListItem> {
        val items = mutableListOf<ListItem>()
        fun section(title: CharSequence, pills: List<Int>, rows: List<WidgetRow>) {
            items += ListItem.Header(title, pills)
            rows.filter { it.kind != RowKind.IDLE }.take(MAX_TILES).chunked(cols).forEach { items += ListItem.Tiles(it) }
        }
        section(buildersLabel(model, t), listOf(PILL_BITE, PILL_BUILDER), model.builderRows)
        section(lctx.getString(R.string.widget_lab), listOf(PILL_SOUP, PILL_RESEARCH), model.labRows)
        section(lctx.getString(R.string.widget_pets), listOf(PILL_PET), listOf(model.petRow))
        val ctPill = if (model.clockTower != null && ClockTowerTracking.enabled(context)) listOf(PILL_CLOCKTOWER) else emptyList()
        if (model.showBuilderBase || ctPill.isNotEmpty()) {
            section(lctx.getString(R.string.widget_bb), ctPill, model.bbRows + listOfNotNull(model.starLabRow))
        }
        return items
    }

    fun tilesView(rows: List<WidgetRow>, model: WidgetModel, live: Boolean, t: Palette, cols: Int = TILES_PER_ROW): RemoteViews {
        val line = RemoteViews(pkg, R.layout.widget_tile_row)
        // Reused list rows keep their old children, so empty the row before adding this one's tiles.
        line.removeAllViews(R.id.tile_row)
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        rows.forEach { line.addView(R.id.tile_row, tile(it, live, nowMs, elapsed, model.accountTag, t)) }
        repeat(cols - rows.size) {
            line.addView(R.id.tile_row, RemoteViews(pkg, R.layout.widget_tile).also { f -> f.setViewVisibility(R.id.tile, View.INVISIBLE) })
        }
        return line
    }

    fun headerView(h: ListItem.Header, model: WidgetModel, t: Palette): RemoteViews {
        val v = RemoteViews(pkg, R.layout.widget_list_header)
        v.setTextViewText(R.id.hdr_label, h.title)
        v.setTextColor(R.id.hdr_label, t.gold)
        for (slot in h.pills) when (slot) {
            PILL_CLOCKTOWER -> model.clockTower?.let { ct ->
                val bmp = icons.bitmapForLevel(GameIds.CLOCK_TOWER, ct.level, (14 * density).roundToInt())
                if (bmp != null) v.setImageViewBitmap(R.id.pill_ct_icon, bmp) else v.setImageViewResource(R.id.pill_ct_icon, R.drawable.ic_clock_tower)
                v.setTextViewText(R.id.pill_ct_text, ct.text)
                v.setTextColor(R.id.pill_ct_text, if (ct.highlighted) t.gold else t.text)
                v.setInt(R.id.pill_ct, "setBackgroundResource", if (ct.highlighted) t.pillOn else t.pillOff)
            }
            PILL_BITE -> pill(v, PillIds(R.id.pill_bite, R.id.pill_bite_add, R.id.pill_bite_undo, R.id.pill_bite_icon, R.id.pill_bite_text),
                model.bite, "builder_bite", R.drawable.ic_builder_bite, model.accountTag, t, true)
            PILL_BUILDER -> pill(v, PillIds(R.id.pill_bpot, R.id.pill_bpot_add, R.id.pill_bpot_undo, R.id.pill_bpot_icon, R.id.pill_bpot_text),
                model.builderPotion, "builder_potion", R.drawable.ic_builder_potion, model.accountTag, t, true)
            PILL_RESEARCH -> pill(v, PillIds(R.id.pill_rpot, R.id.pill_rpot_add, R.id.pill_rpot_undo, R.id.pill_rpot_icon, R.id.pill_rpot_text),
                model.researchPotion, "research_potion", R.drawable.ic_research_potion, model.accountTag, t, true)
            PILL_SOUP -> pill(v, PillIds(R.id.pill_soup, R.id.pill_soup_add, R.id.pill_soup_undo, R.id.pill_soup_icon, R.id.pill_soup_text),
                model.soup, "study_soup", R.drawable.ic_study_soup, model.accountTag, t, true)
            PILL_PET -> pill(v, PillIds(R.id.pill_ppot, R.id.pill_ppot_add, R.id.pill_ppot_undo, R.id.pill_ppot_icon, R.id.pill_ppot_text),
                model.petPotion, "pet_potion", R.drawable.ic_pet_potion, model.accountTag, t, true)
        }
        for (box in listOf(R.id.pill_bite, R.id.pill_soup, R.id.pill_bpot, R.id.pill_rpot, R.id.pill_ppot, R.id.pill_ct)) v.setViewVisibility(box, View.GONE)
        for (slot in h.pills) {
            val box = when (slot) { PILL_BITE -> R.id.pill_bite; PILL_SOUP -> R.id.pill_soup; PILL_BUILDER -> R.id.pill_bpot; PILL_RESEARCH -> R.id.pill_rpot; PILL_CLOCKTOWER -> R.id.pill_ct; else -> R.id.pill_ppot }
            v.setViewVisibility(box, View.VISIBLE)
        }
        return v
    }

    /** Small widget: all running timers, soonest finish first, no section headings. */
    fun smallItems(model: WidgetModel): List<ListItem> =
        model.topTimers(Int.MAX_VALUE).first.map { ListItem.Timer(it) }

    fun timerView(r: WidgetRow, model: WidgetModel, live: Boolean, t: Palette, compact: Boolean = false): RemoteViews =
        row(r, compact, live, System.currentTimeMillis(), SystemClock.elapsedRealtime(), model.accountTag, t, inList = true)

    fun themePalette(theme: String): Palette = palette(theme)

    /** Player name + small gold `TH18`. */
    private fun title(model: WidgetModel, t: Palette): CharSequence {
        val sb = SpannableStringBuilder(model.playerName)
        model.townHall?.let { th ->
            val start = sb.length
            sb.append(" TH$th")
            sb.setSpan(AbsoluteSizeSpan(12, true), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(t.gold), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /** `BUILDERS 6/6` + green ` + Goblin`. */
    private fun buildersLabel(model: WidgetModel, t: Palette): CharSequence {
        val sb = SpannableStringBuilder(model.buildersLabel)
        if (model.goblinWorking) {
            val start = sb.length
            sb.append(" ${model.text.goblinSuffix}")
            sb.setSpan(ForegroundColorSpan(t.goblin), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun footer(model: WidgetModel, t: Palette): CharSequence {
        val sb = SpannableStringBuilder()
        for (part in model.footerLeft) {
            val start = sb.length
            sb.append(part.text)
            val color = when (part.tone) {
                FooterTone.NORMAL -> null
                FooterTone.IDLE -> t.idle
                FooterTone.ERROR -> t.error
            }
            color?.let { sb.setSpan(ForegroundColorSpan(it), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        return sb
    }

    private fun syncIntent(): PendingIntent {
        val intent = Intent(context, SyncActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val MAX_BUILDER_ROWS = 8
        const val PILL_BITE = 0
        const val PILL_BUILDER = 1
        const val PILL_RESEARCH = 2
        const val PILL_PET = 3
        const val PILL_SOUP = 4
        const val PILL_CLOCKTOWER = 5
        const val MAX_TILES = 18
        const val TILES_PER_ROW = 3

        /** Estimated panel height in dp with 30 dp rows (see widget_root.xml / widget_row.xml). */
        fun normalHeightDp(rows: Int): Int = 165 + 33 * rows

        /** Widget height in dp as reported by the launcher (portrait), or null if unknown. */
        fun reportedHeightDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 }

        fun reportedWidthDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 }

        /** Tiles per row: about one per 82 dp of widget width (3 on a normal 4-cell widget), 2 to 5. */
        fun tileColumns(options: Bundle?): Int =
            (((reportedWidthDp(options) ?: 290) - 20) / 82).coerceIn(2, 5)

        /** 25 dp rows when the launcher says 30 dp rows won't fit. */
        fun useCompactRows(options: Bundle?, rows: Int): Boolean =
            reportedHeightDp(options)?.let { it < normalHeightDp(rows) } ?: false
    }
}

/**
 * Icons from `assets/icons/` (64×64 WebP made by tools/gen_icons.cjs; see tools/icon_checklist.txt),
 * scaled to their on-screen size and cached. A hand-added `<name>.png` works too.
 */
class WidgetIcons private constructor(private val context: Context) {

    /** File names present in `assets/icons/`. */
    val available: Set<String> by lazy {
        runCatching { context.assets.list("icons")?.toSet() }.getOrNull() ?: emptySet()
    }

    private val cache = LruCache<String, Bitmap>(64)

    /** `assets/icons/<name>.webp` (or `.png`) scaled to [sizePx], or null if there is no such file. */
    fun bitmap(name: String, sizePx: Int): Bitmap? {
        val file = EXTENSIONS.map { "$name.$it" }.firstOrNull { it in available } ?: return null
        val key = "$file@$sizePx"
        cache.get(key)?.let { return it }
        val decoded = runCatching {
            context.assets.open("icons/$file").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val scaled = if (decoded.width == sizePx && decoded.height == sizePx) decoded else {
            val scale = sizePx.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        cache.put(key, scaled)
        return scaled
    }

    /**
     * The art for [level] of building/trap [id] (`<id>_<level>.webp`); if that level has no file, the
     * nearest lower one, then the plain `<id>` icon. Items without per-level art (heroes, troops) just
     * get the plain icon.
     */
    fun bitmapForLevel(id: Int, level: Int?, sizePx: Int): Bitmap? {
        if (level != null) {
            for (l in level downTo 1) bitmap("${id}_$l", sizePx)?.let { return it }
        }
        return bitmap(id.toString(), sizePx)
    }

    /** Big, sharp Town Hall art for the app (`assets/townhall/th_<level>.webp`, 384 px); the nearest lower level if one is missing. */
    fun townHall(level: Int?, sizePx: Int): Bitmap? {
        val top = (level ?: return null).coerceIn(1, 40)
        for (l in top downTo 1) {
            val key = "th_$l@$sizePx"
            cache.get(key)?.let { return it }
            val decoded = runCatching {
                context.assets.open("townhall/th_$l.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: continue
            val scale = sizePx.toFloat() / maxOf(decoded.width, decoded.height)
            val out = if (scale >= 1f) decoded else Bitmap.createScaledBitmap(
                decoded, (decoded.width * scale).roundToInt().coerceAtLeast(1), (decoded.height * scale).roundToInt().coerceAtLeast(1), true,
            )
            cache.put(key, out)
            return out
        }
        return null
    }

    /** Which asset file [bitmapForLevel] would use for these ids (first that exists), or null. For diagnostics. */
    fun fileFor(ids: List<Int>, level: Int?): String? {
        fun find(name: String) = EXTENSIONS.map { "$name.$it" }.firstOrNull { it in available }
        for (id in ids) {
            if (level != null) for (l in level downTo 1) find("${id}_$l")?.let { return it }
            find(id.toString())?.let { return it }
        }
        return null
    }

    companion object {
        private val EXTENSIONS = listOf("webp", "png")

        @Volatile private var instance: WidgetIcons? = null

        fun get(context: Context): WidgetIcons = instance ?: synchronized(this) {
            instance ?: WidgetIcons(context.applicationContext).also { instance = it }
        }
    }
}
