package com.killersparrow1.clashtrackers.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.killersparrow1.clashtrackers.core.AccountSettings
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.Finish
import com.killersparrow1.clashtrackers.core.FinishNotices
import com.killersparrow1.clashtrackers.core.WidgetActions
import com.killersparrow1.clashtrackers.core.WidgetModelBuilder
import com.killersparrow1.clashtrackers.core.WidgetSchedule
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.data.StateRepository
import com.killersparrow1.clashtrackers.data.Times
import com.killersparrow1.clashtrackers.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The home-screen widget, drawn with plain RemoteViews (not Glance) so it can use the Lilita One
 * font, Chronometer countdowns, progress bars and the design's drawables.
 * The class name is unchanged from the Glance version so a placed widget keeps working.
 *
 * Also the app's single broadcast entry point: widget button taps (step 5), the redraw and
 * finish alarms, and boot / app update / clock change, which all end in [WidgetUpdater.updateAll].
 */
class ClashWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, "widget")

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context, "widget")

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        runAsync {
            val repo = StateRepository(context.applicationContext)
            for (id in appWidgetIds) {
                repo.removeWidgetAccount(id)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> refresh(context, "widget")
            ACTION_FINISH -> refresh(context, "alarm")
            ACTION_TAP -> tap(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, ACTION_QUICKBOOT,
            -> refresh(context, "system")
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) = WidgetAlarm.cancelRedraw(context)

    private fun refresh(context: Context, source: String) = runAsync { WidgetUpdater.updateAll(context, source) }

    /** A widget button: `clashtracker://tap/<action>/<arg>?account=<tag>`. Saved, logged, then redrawn at once. */
    private fun tap(context: Context, intent: Intent) {
        val segments = intent.data?.pathSegments.orEmpty()
        val action = segments.getOrNull(0) ?: return
        val arg = segments.getOrNull(1) ?: return
        val accountTag = intent.data?.getQueryParameter("account")
        runAsync {
            val app = context.applicationContext
            val repo = StateRepository(app)
            try {
                val accounts = repo.accounts.first()
                val selected = repo.selectedAccountTag.first()
                val tag = accountTag ?: selected ?: accounts.firstOrNull()?.tag ?: return@runAsync
                val settings = repo.getAccountSettings(tag)
                val now = System.currentTimeMillis() / 1000
                repo.updateAccountState(tag) { before ->
                    WidgetActions.apply(before, action, arg, now, settings.boosts)
                }
                repo.log("tap", null, "Tap on $tag: $action/$arg")
            } catch (t: Throwable) {
                repo.log("tap", false, "Tap failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            WidgetUpdater.updateAll(app, "tap")
        }
    }

    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.killersparrow1.clashtrackers.WIDGET_TICK"
        const val ACTION_FINISH = "com.killersparrow1.clashtrackers.JOB_FINISH"
        const val ACTION_TAP = "com.killersparrow1.clashtrackers.WIDGET_TAP"
        /** Some ROMs send this instead of BOOT_COMPLETED after a fast reboot. */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

object WidgetUpdater {

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClashWidgetReceiver::class.java))

    /**
     * Everything that follows a change or an alarm: posts "… free" notifications for jobs that just
     * finished across all accounts, re-renders every placed widget with its account's state, and schedules alarms.
     */
    suspend fun updateAll(context: Context, source: String): Int {
        val app = context.applicationContext
        val repo = StateRepository(app)
        return try {
            val names = Names.get(app)
            val nowMs = System.currentTimeMillis()
            val now = nowMs / 1000

            val accounts = repo.accounts.first()
            val selectedTag = repo.selectedAccountTag.first()
            val globalSettings = repo.currentSettings()

            val allFinishTimes = mutableListOf<Long>()
            for (acct in accounts) {
                val state = repo.getAccountState(acct.tag)
                val acctSettings = repo.getAccountSettings(acct.tag)
                val settings = AppSettings(
                    totalBuildersOverride = acctSettings.totalBuildersOverride,
                    boosts = acctSettings.boosts,
                    notificationsEnabled = globalSettings.notificationsEnabled,
                    liveCountdown = globalSettings.liveCountdown,
                    playerName = acctSettings.playerName,
                )
                try {
                    val r = FinishNotices.check(state, settings, names, now)
                    val fresh = r.notices.filter { it.jobKey !in state.notified }
                    repo.updateAccountState(acct.tag) { st ->
                        st.copy(notified = r.notified)
                    }
                    if (fresh.isNotEmpty()) {
                        if (globalSettings.notificationsEnabled) {
                            val posted = Notifier.post(app, fresh, acct.displayName, acct.tag, accounts.size > 1)
                            repo.log(source, null, if (posted) "Notified (${acct.tag}): " + fresh.joinToString("; ") { it.title }
                                else "Not notified (notifications are blocked for the app): " + fresh.joinToString("; ") { it.title })
                        } else {
                            repo.log(source, null, "Finished (${acct.tag}, notifications off): " + fresh.joinToString("; ") { it.title })
                        }
                    }
                } catch (t: Throwable) {
                    repo.log(source, false, "Notification failed (${acct.tag}): ${t.javaClass.simpleName}: ${t.message}")
                }

                FinishNotices.alarmAt(state, settings, now)?.let { allFinishTimes.add(it) }
            }

            WidgetAlarm.scheduleFinish(app, allFinishTimes.minOrNull()?.times(1000))

            val manager = AppWidgetManager.getInstance(app)
            val ids = widgetIds(app)
            if (ids.isEmpty()) {
                WidgetAlarm.cancelRedraw(app)
                return 0
            }

            val renderer = WidgetRenderer(app)
            val nextUpdates = mutableListOf<Long>()
            val fallbackTag = selectedTag ?: accounts.firstOrNull()?.tag

            for (id in ids) {
                val tag = repo.getWidgetAccount(id) ?: fallbackTag
                val state = if (tag != null) repo.getAccountState(tag) else repo.currentState()
                val acctSettings = if (tag != null) repo.getAccountSettings(tag) else AccountSettings()
                val settings = AppSettings(
                    totalBuildersOverride = acctSettings.totalBuildersOverride,
                    boosts = acctSettings.boosts,
                    notificationsEnabled = globalSettings.notificationsEnabled,
                    liveCountdown = globalSettings.liveCountdown,
                    playerName = acctSettings.playerName,
                )

                val theme = repo.getWidgetTheme(id)
                val model = WidgetModelBuilder.build(state, settings, names, Times.get(app), now, tag ?: "", theme)
                manager.updateAppWidget(id, renderer.render(model, settings.liveCountdown, manager.getAppWidgetOptions(id)))

                WidgetSchedule.nextUpdateMs(model, nowMs, settings.liveCountdown)?.let { nextUpdates.add(it) }
            }
            WidgetAlarm.scheduleRedraw(app, nextUpdates.minOrNull())
            ids.size
        } catch (t: Throwable) {
            repo.log(source, false, "Widget refresh failed: ${t.javaClass.simpleName}: ${t.message}")
            -1
        }
    }
}

object WidgetAlarm {
    private const val SLACK_MS = 250L
    private const val MIN_DELAY_MS = 1_000L

    private fun intent(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, ClashWidgetReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun scheduleRedraw(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ClashWidgetReceiver.ACTION_TICK, 1)
        if (atMs == null) return am.cancel(pi)
        val at = maxOf(atMs + SLACK_MS, System.currentTimeMillis() + MIN_DELAY_MS)
        try {
            if (canScheduleExact(context)) am.setExact(AlarmManager.RTC, at, pi)
            else am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
        }
    }

    fun scheduleFinish(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ClashWidgetReceiver.ACTION_FINISH, 2)
        if (atMs == null) return am.cancel(pi)
        val at = maxOf(atMs + SLACK_MS, System.currentTimeMillis() + MIN_DELAY_MS)
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelRedraw(context: Context) = scheduleRedraw(context, null)
}
