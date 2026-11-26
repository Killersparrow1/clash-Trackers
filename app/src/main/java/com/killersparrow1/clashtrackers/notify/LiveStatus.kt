package com.killersparrow1.clashtrackers.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.RowKind
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.widget.WidgetIcons

/**
 * An ongoing, silent notification that counts down to the next builder finishing. It shows the upgrading item's picture,
 * and on Android 16+ it asks to be a Live Update, which phones show as a chip in the status bar.
 */
object LiveStatus {
    private const val PREFS = "notif_prefs"
    private const val KEY_ON = "live_status"
    private const val KEY_CHIP = "live_status_chip"
    private const val CHANNEL = "live_status_v1"
    private const val NOTIFICATION_ID = 7301

    const val CHIP_COUNTDOWN = 0
    const val CHIP_FINISH_TIME = 1
    const val CHIP_FREE_BUILDERS = 2

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    fun chip(context: Context): Int = CHIP_COUNTDOWN // the chip counts down; the other details are in the notification text
    fun setChip(context: Context, chip: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_CHIP, chip).apply()
    }

    /** Posts, updates or removes the notification so it matches [model] (the selected village's builders). */
    fun update(context: Context, model: WidgetModel?) {
        val nm = NotificationManagerCompat.from(context)
        val next = model?.builderRows
            ?.filter { it.kind == RowKind.RUNNING && !it.goblin && it.finishAt != null }
            ?.minByOrNull { it.finishAt!! }
        if (!enabled(context) || !Notifier.allowed(context) || model == null || next == null) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel(context)
        val finishMs = next.finishAt!! * 1000
        val busy = model.buildersBusy
        val total = model.buildersTotal
        val free = (total - busy).coerceAtLeast(0)
        val icon = runCatching { WidgetIcons.get(context).bitmapForLevel(next.iconIds.firstOrNull() ?: 0, next.iconLevel, 128) }.getOrNull()
        val finishTime = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(finishMs))
        val chipText = when (chip(context)) {
            CHIP_FINISH_TIME -> finishTime
            CHIP_FREE_BUILDERS -> "$free/$total"
            else -> null // the system counts down by itself
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.live_status_title, next.label))
            .setContentText(context.getString(R.string.live_status_text, finishTime, busy, total, free))
            .setWhen(finishMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(Notifier.openAppIntent(context))
            // Swiping it away brings it straight back; turning the option off removes it for good.
            .setDeleteIntent(
                android.app.PendingIntent.getBroadcast(
                    context, 7302, android.content.Intent(context, LiveStatusReturnReceiver::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (icon != null) builder.setLargeIcon(icon)
        // Android 16 Live Updates: these are plain extras, so older versions simply ignore them.
        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            if (chipText != null) putString("android.shortCriticalText", chipText)
        }
        builder.addExtras(extras)
        try {
            var notification = builder.build()
            if (Build.VERSION.SDK_INT >= 36) {
                // The system's own setters, in case the plain extras are not read for the chip text.
                val b = android.app.Notification.Builder.recoverBuilder(context, notification).setRequestPromotedOngoing(true)
                if (chipText != null) b.setShortCriticalText(chipText)
                notification = b.build()
            }
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were blocked in the meantime.
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val lctx = com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(context)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, lctx.getString(R.string.live_status_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = lctx.getString(R.string.live_status_channel_desc)
                setShowBadge(false)
            },
        )
    }
}
