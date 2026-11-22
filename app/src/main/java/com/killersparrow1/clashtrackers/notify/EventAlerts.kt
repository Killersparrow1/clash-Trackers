package com.killersparrow1.clashtrackers.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.killersparrow1.clashtrackers.MainActivity
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.EventAlertPlanner
import com.killersparrow1.clashtrackers.core.GameEvents
import com.killersparrow1.clashtrackers.update.ClashKingDatesStore
import com.killersparrow1.clashtrackers.update.EventsStore
import com.killersparrow1.clashtrackers.widget.WidgetAlarm

/**
 * Notifications for the Clash tab's events (clan games, raid weekend, CWL, season end, league reset). One alarm is kept
 * for the next alert; when it fires, the alerts that are due are shown and the next alarm is set. The plan itself is
 * [EventAlertPlanner]. The sound is res/raw/event_sound (a channel's sound is fixed once created, so a new sound needs a new channel id).
 */
object EventAlerts {
    private const val PREFS = "event_alerts"
    private const val ACTION = "com.killersparrow1.clashtrackers.EVENT_ALERT"
    private const val CLASH_PACKAGE = "com.supercell.clashofclans"
    private const val CHANNEL = "events_v4"

    val EVENT_IDS = listOf(
        GameEvents.CLAN_GAMES, GameEvents.RAID_WEEKEND, GameEvents.CWL_SIGNUP,
        GameEvents.CWL_WARS, GameEvents.SEASON_END, GameEvents.LEAGUE_RESET,
    )

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, on: Boolean) { p(c).edit().putBoolean("enabled", on).apply(); schedule(c) }

    fun eventOn(c: Context, id: String) = p(c).getBoolean("ev_$id", true)
    fun setEventOn(c: Context, id: String, on: Boolean) { p(c).edit().putBoolean("ev_$id", on).apply(); schedule(c) }

    fun leadSec(c: Context) = p(c).getLong("lead", 6 * 3600L)
    fun setLeadSec(c: Context, sec: Long) { p(c).edit().putLong("lead", sec).apply(); schedule(c) }

    private fun enabledIds(c: Context): Set<String> =
        if (!enabled(c)) emptySet() else EVENT_IDS.filter { eventOn(c, it) }.toSet() + extraIds(c)

    /** One-off events from the downloaded list follow the master switch. */
    private fun extraIds(c: Context): Set<String> = EventsStore.config.extra.map { it.id }.toSet()

    private fun fired(c: Context): Set<String> = p(c).getStringSet("fired", emptySet()) ?: emptySet()

    private fun currentEvents(c: Context, now: Long): List<com.killersparrow1.clashtrackers.core.GameEvent> {
        EventsStore.load(c)
        ClashKingDatesStore.load(c)
        return GameEvents.upcoming(now, EventsStore.config, ClashKingDatesStore.dates)
    }

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 31, Intent(c, EventAlertReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets (or clears) the one alarm for the next alert. Cheap; called at start-up, after settings change and after refreshes. */
    fun schedule(c: Context) {
        val ctx = c.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(ctx)
        val now = System.currentTimeMillis() / 1000
        val ids = enabledIds(ctx)
        val events = currentEvents(ctx, now)
        val alerts = EventAlertPlanner.alerts(events, ids, leadSec(ctx))
        val wake = EventAlertPlanner.nextWake(alerts, events, ids, fired(ctx), now) ?: return am.cancel(pi)
        val atMs = wake * 1000
        try {
            if (WidgetAlarm.canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    /** Shows what is due, remembers it, and sets the next alarm. */
    fun fire(c: Context) {
        val ctx = c.applicationContext
        val now = System.currentTimeMillis() / 1000
        val ids = enabledIds(ctx)
        val events = currentEvents(ctx, now)
        val due = EventAlertPlanner.due(EventAlertPlanner.alerts(events, ids, leadSec(ctx)), fired(ctx), now)
        if (due.isNotEmpty()) {
            if (Notifier.allowed(ctx)) due.forEach { post(ctx, it) }
            // Keep only recent keys so the list does not grow for ever.
            val keep = (fired(ctx) + due.map { it.key }).toList().takeLast(60).toSet()
            p(ctx).edit().putStringSet("fired", keep).apply()
        }
        schedule(ctx)
    }

    private fun ensureChannel(c: Context): String {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm?.deleteNotificationChannel("events_v1") // older sound files
        nm?.deleteNotificationChannel("events_v2")
        nm?.deleteNotificationChannel("events_v3")
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            val lctx = com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(c)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, lctx.getString(R.string.ev_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = lctx.getString(R.string.ev_channel_desc)
                    setSound(Uri.parse("android.resource://${c.packageName}/${R.raw.event_sound}"), attrs)
                },
            )
        }
        return CHANNEL
    }

    private fun leadLabel(c: Context, sec: Long): String = c.getString(
        when {
            sec <= 3600L -> R.string.ev_lead_1h
            sec <= 6 * 3600L -> R.string.ev_lead_6h
            else -> R.string.ev_lead_24h
        },
    )

    private fun eventName(c: Context, a: EventAlertPlanner.Alert): String = a.name ?: c.getString(
        when (a.eventId) {
            GameEvents.CLAN_GAMES -> R.string.clash_ev_clan_games
            GameEvents.RAID_WEEKEND -> R.string.clash_ev_raid
            GameEvents.CWL_SIGNUP -> R.string.clash_ev_cwl_signup
            GameEvents.CWL_WARS -> R.string.clash_ev_cwl_wars
            GameEvents.SEASON_END -> R.string.clash_ev_season_end
            else -> R.string.clash_ev_league_reset
        },
    )

    private fun post(c: Context, a: EventAlertPlanner.Alert) {
        val name = eventName(c, a)
        val lead = leadLabel(c, leadSec(c))
        val title = when (a.kind) {
            EventAlertPlanner.Kind.STARTS -> c.getString(R.string.ev_alert_started, name)
            EventAlertPlanner.Kind.ENDING -> c.getString(R.string.ev_alert_ending, name, lead)
            EventAlertPlanner.Kind.COMING -> c.getString(R.string.ev_alert_coming, name, lead)
        }
        notify(c, a.key.hashCode(), title)
    }

    /** A sample alert, so the sound can be heard. */
    fun test(c: Context): Boolean {
        if (!Notifier.allowed(c)) return false
        notify(c, 7100, c.getString(R.string.ev_alert_test))
        return true
    }

    private fun notify(c: Context, id: Int, title: String) {
        val channel = ensureChannel(c)
        val launch = c.packageManager.getLaunchIntentForPackage(CLASH_PACKAGE) ?: Intent(c, MainActivity::class.java)
        val open = PendingIntent.getActivity(c, 0, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(c.getString(R.string.ev_alert_tap))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (_: SecurityException) { }
    }
}
