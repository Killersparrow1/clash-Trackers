package com.killersparrow1.clashtrackers.companion

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.notify.Notifier
import com.killersparrow1.clashtrackers.sync.SyncActivity

/**
 * Clash Companion. Event driven (window changes only, no polling): when Clash of Clans comes to the front it shows a
 * small draggable "Paste JSON" bar and a quiet notification with the same action; both go away when another app is
 * in front. The accessibility overlay type needs no "draw over other apps" permission, and the service never reads
 * screen content. Importing itself is [SyncActivity] -> SyncRunner, the same path as the widget's Sync button.
 */
class CompanionService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var gameActive = false
    private var barDismissed = false
    private var bar: LinearLayout? = null
    private var label: TextView? = null
    private var status: String? = null
    private val clearStatus = Runnable { status = null; label?.text = getString(R.string.companion_paste); postNotification() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val next = CompanionLogic.next(event.packageName?.toString(), gameActive, packageName, keyboards())
        if (next == gameActive) return
        gameActive = next
        if (!next) barDismissed = false // the ✕ lasts until the game is left; coming back shows the bar again
        apply()
    }

    private fun keyboards(): Set<String> = runCatching {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    /** Brings the bar and notification in line with the game state and the settings. */
    fun apply() {
        val on = gameActive && CompanionPrefs.enabled(this)
        if (on && CompanionPrefs.bar(this) && !barDismissed) showBar() else hideBar()
        if (on && CompanionPrefs.notification(this)) postNotification() else cancelNotification()
    }

    /** Result of an import started from the bar or the notification. */
    fun showResult(ok: Boolean, message: String) {
        status = (if (ok) "✓ " else "✕ ") + message
        label?.text = status
        postNotification()
        handler.removeCallbacks(clearStatus)
        handler.postDelayed(clearStatus, 5000)
    }

    private fun pasteIntent() = Intent(this, SyncActivity::class.java)
        .putExtra(SyncActivity.EXTRA_WIDGET_TAP, true)
        .putExtra(SyncActivity.EXTRA_SOURCE, "companion")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    private fun showBar() {
        if (bar != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        fun tv(text: String, click: () -> Unit) = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            setOnClickListener { click() }
        }
        val paste = tv(getString(R.string.companion_paste)) { startActivity(pasteIntent()) }
        val close = tv("✕") { barDismissed = true; hideBar() }
        label = paste
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(0xEE1F2A44.toInt()); cornerRadius = 28 * d }
            elevation = 6 * d
            addView(paste)
            addView(close)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (16 * d).toInt(); y = (120 * d).toInt() }
        // Drag by the bar background/label; a plain tap still reaches the buttons.
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; false }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - sx) > 12 * d || kotlin.math.abs(e.rawY - sy) > 12 * d) moved = true
                    if (moved) { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); runCatching { wm.updateViewLayout(view, lp) } }
                    moved
                }
                else -> moved
            }
        }
        runCatching { wm.addView(view, lp); bar = view }
        if (status != null) label?.text = status
    }

    private fun hideBar() {
        val v = bar ?: return
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        bar = null; label = null
    }

    private fun postNotification() {
        if (!Notifier.allowed(this) || !gameActive || !CompanionPrefs.enabled(this) || !CompanionPrefs.notification(this)) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.companion_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = getString(R.string.companion_channel_desc); setShowBadge(false) },
            )
        }
        val pi = PendingIntent.getActivity(this, 7, pasteIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(getString(R.string.companion_notif_title))
                .setContentText(status ?: getString(R.string.companion_notif_text))
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, getString(R.string.companion_paste), pi)
                .build(),
        )
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        hideBar(); cancelNotification()
        gameActive = false
        if (instance === this) instance = null
    }

    companion object {
        private const val CHANNEL = "companion"
        private const val NOTIFICATION_ID = 7001
        @Volatile var instance: CompanionService? = null
            private set

        /** Called after a setting changes. */
        fun refresh() { instance?.let { s -> s.handler.post { s.apply() } } }

        /** Called by the paste screen when an import from the companion finished. */
        fun report(ok: Boolean, message: String) { instance?.let { s -> s.handler.post { s.showResult(ok, message) } } }
    }
}
