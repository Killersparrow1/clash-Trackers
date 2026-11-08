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

    private data class Palette(
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
    )

    private fun palette(theme: String): Palette = when (theme) {
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
            0xFFF5F7FA.toInt(), 0xFFB8C4D0.toInt(), 0xFFFFD24A.toInt(), 0xFF7BE07A.toInt(),
            0xFFFFB35C.toInt(), 0xFFFF8A8A.toInt(), 0xFF101418.toInt(),
        )
        "light" -> Palette(
            R.drawable.widget_panel_light, R.drawable.row_slot_light, R.drawable.row_done_light,
            R.drawable.pill_on_light, R.drawable.pill_off_light, R.drawable.icon_slot_light,
            R.drawable.btn_sync_light, R.drawable.btn_plus_light, R.drawable.btn_helper_light,
            0xFF2B2118.toInt(), 0xFF6B5A47.toInt(), 0xFF8A5A10.toInt(), 0xFF1F6D2A.toInt(),
            0xFFA34E00.toInt(), 0xFFB3261E.toInt(), 0xFF3A2500.toInt(),
        )
        else -> Palette(
            R.drawable.widget_panel, R.drawable.row_slot, R.drawable.row_done,
            R.drawable.pill_on, R.drawable.pill_off, R.drawable.icon_slot,
            R.drawable.btn_sync, R.drawable.btn_plus, R.drawable.btn_helper,
            COLOR_WHITE, COLOR_SUB, COLOR_GOLD, COLOR_GOBLIN, COLOR_IDLE, COLOR_ERROR, COLOR_SYNC_TEXT,
        )
    }

    fun render(model: WidgetModel, liveCountdown: Boolean, options: Bundle?): RemoteViews {
        val heightDp = reportedHeightDp(options) ?: 320
        val isSmall = heightDp < 200

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val smallSize = android.util.SizeF(250f, 130f)
            val largeSize = android.util.SizeF(250f, 320f)
            val smallRv = renderSmall(model, liveCountdown, options)
            val largeRv = renderLarge(model, liveCountdown, options)
            return RemoteViews(mapOf(smallSize to smallRv, largeSize to largeRv))
        } else {
            return if (isSmall) renderSmall(model, liveCountdown, options) else renderLarge(model, liveCountdown, options)
        }
    }

    private fun renderLarge(model: WidgetModel, liveCountdown: Boolean, options: Bundle?): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root)
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

        rv.setTextViewText(R.id.builders_label, buildersLabel(model, t))
        rv.setTextColor(R.id.builders_label, t.gold)
        rv.setTextViewText(R.id.lbl_lab, lctx.getString(R.string.widget_lab))
        rv.setTextColor(R.id.lbl_lab, t.gold)
        rv.setTextViewText(R.id.lbl_pets, lctx.getString(R.string.widget_pets))
        rv.setTextColor(R.id.lbl_pets, t.gold)
        rv.setTextViewText(R.id.lbl_bb, lctx.getString(R.string.widget_bb))
        rv.setTextColor(R.id.lbl_bb, t.gold)
        pill(rv, PillIds(R.id.pill_bite, R.id.pill_bite_add, R.id.pill_bite_undo, R.id.pill_bite_icon, R.id.pill_bite_text),
            model.bite, "builder_bite", R.drawable.ic_builder_bite, model.accountTag, t)
        pill(rv, PillIds(R.id.pill_bpot, R.id.pill_bpot_add, R.id.pill_bpot_undo, R.id.pill_bpot_icon, R.id.pill_bpot_text),
            model.builderPotion, "builder_potion", R.drawable.ic_builder_potion, model.accountTag, t)
        pill(rv, PillIds(R.id.pill_rpot, R.id.pill_rpot_add, R.id.pill_rpot_undo, R.id.pill_rpot_icon, R.id.pill_rpot_text),
            model.researchPotion, "research_potion", R.drawable.ic_research_potion, model.accountTag, t)
        pill(rv, PillIds(R.id.pill_ppot, R.id.pill_ppot_add, R.id.pill_ppot_undo, R.id.pill_ppot_icon, R.id.pill_ppot_text),
            model.petPotion, "pet_potion", R.drawable.ic_pet_potion, model.accountTag, t)

        val compact = useCompactRows(options, model.allRows.size)
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        fun fill(@IdRes container: Int, rows: List<WidgetRow>) {
            rv.removeAllViews(container)
            rows.take(MAX_BUILDER_ROWS).forEach { rv.addView(container, row(it, compact, liveCountdown, nowMs, elapsed, model.accountTag, t)) }
        }
        fill(R.id.rows_builders, model.builderRows)
        fill(R.id.rows_lab, model.labRows)
        fill(R.id.rows_pet, listOf(model.petRow))
        // Builder Base section: hidden unless a night village builder or the Star Lab is busy.
        rv.setViewVisibility(R.id.bb_section, if (model.showBuilderBase) View.VISIBLE else View.GONE)
        if (model.showBuilderBase) {
            fill(R.id.rows_bb, model.bbRows)
            fill(R.id.rows_starlab, listOfNotNull(model.starLabRow))
        }

        rv.setTextViewText(R.id.foot_left, footer(model, t))
        rv.setTextColor(R.id.foot_left, t.sub)
        rv.setTextViewText(R.id.foot_right, model.footerRight)
        rv.setTextColor(R.id.foot_right, t.sub)
        return rv
    }

    private fun renderSmall(model: WidgetModel, liveCountdown: Boolean, options: Bundle?): RemoteViews {
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

        val heightDp = reportedHeightDp(options) ?: 150
        val maxRows = if (heightDp < 140) 2 else 3
        val (topRows, hidden) = model.topTimers(maxRows)
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()

        rv.removeAllViews(R.id.rows_small)
        topRows.forEach { r ->
            rv.addView(R.id.rows_small, row(r, compact = true, liveCountdown, nowMs, elapsed, model.accountTag, t))
        }

        if (hidden > 0) {
            rv.setViewVisibility(R.id.foot_more, View.VISIBLE)
            rv.setTextViewText(R.id.foot_more, lctx.getString(R.string.widget_more, hidden))
            rv.setTextColor(R.id.foot_more, t.idle)
        } else {
            rv.setViewVisibility(R.id.foot_more, View.GONE)
        }
        return rv
    }

    private fun row(model: WidgetRow, compact: Boolean, live: Boolean, nowMs: Long, elapsed: Long, accountTag: String, t: Palette): RemoteViews {
        // The model already marks a row done Finish.GRACE_SEC early. This also covers a model built a
        // moment before this render, so a chronometer is never started at or past its finish.
        val row = if (model.kind == RowKind.RUNNING && model.finishAt != null && Finish.isDone(model.finishAt, nowMs / 1000)) {
            model.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            model
        }
        val v = RemoteViews(pkg, if (compact) R.layout.widget_row_compact else R.layout.widget_row)
        val iconPx = ((if (compact) 20 else 24) * density).roundToInt()
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
                v.setProgressBar(R.id.row_fill, 1000, (row.progress * 1000).roundToInt(), false)
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
                v.setViewVisibility(R.id.row_fill, View.GONE)
                v.setViewVisibility(R.id.row_clock, View.GONE)
                v.setViewVisibility(R.id.row_chrono, View.GONE)
                v.setViewVisibility(R.id.row_text, View.VISIBLE)
                v.setTextViewText(R.id.row_text, row.staticStatus)
                if (row.kind == RowKind.COMPLETED) v.setViewVisibility(R.id.row_check, View.VISIBLE)
            }
        }

        if (row.button != RowButton.NONE && row.jobKey != null) {
            v.setOnClickPendingIntent(R.id.row_btn, tapIntent(WidgetActions.HELPER, row.jobKey, accountTag))
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

    private fun pill(rv: RemoteViews, ids: PillIds, pill: BoostPill, asset: String, @DrawableRes fallback: Int, accountTag: String, t: Palette) {
        val bmp = icons.bitmap(asset, (14 * density).roundToInt())
        if (bmp != null) rv.setImageViewBitmap(ids.icon, bmp) else rv.setImageViewResource(ids.icon, fallback)
        rv.setTextViewText(ids.text, pill.text)
        rv.setTextColor(ids.text, if (pill.active) t.gold else t.text)
        rv.setInt(ids.box, "setBackgroundResource", if (pill.active || pill.canUndo) t.pillOn else t.pillOff)
        rv.setOnClickPendingIntent(ids.add, tapIntent(WidgetActions.ADD_BOOST, pill.type.name, accountTag))
        rv.setViewVisibility(ids.undo, if (pill.canUndo) View.VISIBLE else View.GONE)
        if (pill.canUndo) rv.setOnClickPendingIntent(ids.undo, tapIntent(WidgetActions.UNDO_BOOST, pill.type.name, accountTag))
    }

    /**
     * Broadcast to [ClashWidgetReceiver] for a button tap, handled in the background (no activity, no popup).
     * The URI makes every action + argument a distinct PendingIntent.
     */
    private fun tapIntent(action: String, arg: String, accountTag: String): PendingIntent {
        val uri = Uri.Builder().scheme("clashtracker").authority("tap").appendPath(action).appendPath(arg)
            .appendQueryParameter("account", accountTag).build()
        val intent = Intent(context, ClashWidgetReceiver::class.java)
            .setAction(ClashWidgetReceiver.ACTION_TAP)
            .setData(uri)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

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

        /** Estimated panel height in dp with 30 dp rows (see widget_root.xml / widget_row.xml). */
        fun normalHeightDp(rows: Int): Int = 165 + 33 * rows

        /** Widget height in dp as reported by the launcher (portrait), or null if unknown. */
        fun reportedHeightDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 }

        fun reportedWidthDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 }

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

    companion object {
        private val EXTENSIONS = listOf("webp", "png")

        @Volatile private var instance: WidgetIcons? = null

        fun get(context: Context): WidgetIcons = instance ?: synchronized(this) {
            instance ?: WidgetIcons(context.applicationContext).also { instance = it }
        }
    }
}
