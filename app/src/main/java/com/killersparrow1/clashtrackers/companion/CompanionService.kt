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
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
    private var bar: View? = null
    private var badge: TextView? = null
    private var status: String? = null
    private var lastResult: Boolean? = null
    private val clearStatus = Runnable { status = null; lastResult = null; showBadge(); postNotification() }

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
        lastResult = ok
        showBadge()
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
        val dm = resources.displayMetrics
        val d = dm.density
        val size = (56 * d).toInt()
        // The app's own gold game-style button (same drawable family as the widget's Sync button) with its refresh arrow.
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_sync)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding((13 * d).toInt(), (11 * d).toInt(), (13 * d).toInt(), (15 * d).toInt())
        }
        val badge = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.GRAY); setStroke((1.5f * d).toInt(), Color.WHITE) }
        }
        val view = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.btn_sync_clash)
            elevation = 6 * d
            contentDescription = getString(R.string.companion_paste)
            addView(icon, FrameLayout.LayoutParams(size, size))
            addView(badge, FrameLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt(), Gravity.TOP or Gravity.END))
        }
        this.badge = badge
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Default: just above the Build button, under Shop; drag to fine-tune, the spot is remembered.
            val (fx, fy) = CompanionPrefs.barPosition(this@CompanionService)
            x = (dm.widthPixels * fx - size / 2f).toInt()
            y = (dm.heightPixels * fy - size / 2f).toInt()
        }
        view.setOnClickListener { startActivity(pasteIntent()) }
        // One handler for tap, drag and long-press. (A framework long-click listener keeps running while a drag is
        // consumed here, which hid the button mid-drag, so the long-press is timed by hand and cancelled by movement.)
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        val longPress = Runnable { if (!moved) { barDismissed = true; hideBar() } }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                    handler.postDelayed(longPress, 700)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - sx) > 10 * d || kotlin.math.abs(e.rawY - sy) > 10 * d) {
                        moved = true
                        handler.removeCallbacks(longPress)
                    }
                    if (moved) {
                        lp.x = (ox + (e.rawX - sx)).toInt().coerceIn(0, dm.widthPixels - size)
                        lp.y = (oy + (e.rawY - sy)).toInt().coerceIn(0, dm.heightPixels - size)
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (moved) CompanionPrefs.setBarPosition(this, (lp.x + size / 2f) / dm.widthPixels, (lp.y + size / 2f) / dm.heightPixels)
                    else v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            true
        }
        runCatching { wm.addView(view, lp); bar = view }
        showBadge()
    }

    /** Small ✓ (green) or ✕ (red) on the button after an import, so the result is visible without leaving the game. */
    private fun showBadge() {
        val b = badge ?: return
        val r = lastResult
        if (r == null) { b.visibility = View.GONE; return }
        b.text = if (r) "✓" else "✕"
        (b.background as? GradientDrawable)?.setColor(if (r) 0xFF2E9E4F.toInt() else 0xFFD13B3B.toInt())
        b.visibility = View.VISIBLE
    }

    private fun hideBar() {
        val v = bar ?: return
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        bar = null; badge = null
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
