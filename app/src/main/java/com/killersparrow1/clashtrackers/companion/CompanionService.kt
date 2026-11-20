package com.killersparrow1.clashtrackers.companion

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.notify.Notifier
import com.killersparrow1.clashtrackers.sync.SyncActivity

/**
 * Clash Companion. Event driven (window changes and a few one-shot timers, no polling): while Clash of Clans is in
 * front it offers a one-tap village import as a small golden button and as a quiet notification action.
 *
 * What this can and cannot know: accessibility only reports which app's window is in front. Clash of Clans draws
 * everything itself in one window, so the home village, an attack and a menu all look the same here. The button
 * therefore shows after [CompanionSession.START_DELAY_MS] in a game session and is hidden whenever another app is in
 * front; it cannot hide itself during a battle. A double-tap hides it for the rest of the session.
 *
 * The overlay is a TYPE_ACCESSIBILITY_OVERLAY window (no "draw over other apps" permission), the service never reads
 * screen content, and importing is [SyncActivity] -> SyncRunner, the same path as the widget's Sync button.
 */
class CompanionService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val session = CompanionSession()
    private var bar: View? = null
    private var icon: ImageView? = null
    private var badge: TextView? = null
    private var pulse: ObjectAnimator? = null
    private var fetching = false
    private var barPlacing = false
    private var lastTapAt = 0L
    private var status: String? = null

    private val applyLater = Runnable { apply() }
    private val fetchTimeout = Runnable { report(false, getString(R.string.companion_timeout)) }
    private val clearBadge = Runnable { badge?.visibility = View.GONE; status = null; postNotification() }

    private fun now() = SystemClock.elapsedRealtime()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val next = CompanionLogic.next(event.packageName?.toString(), session.active, packageName, keyboards())
        if (next == session.active) return
        if (next) session.onGameEntered(now()) else session.onGameLeft(now())
        apply()
    }

    private fun keyboards(): Set<String> = runCatching {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (bar != null) { hideBar(); apply() } // placed again for the new screen size
    }

    /** Brings the button and notification in line with the game state, the session and the settings. */
    fun apply() {
        handler.removeCallbacks(applyLater)
        val enabled = session.active && CompanionPrefs.enabled(this)
        val t = now()
        // Until a spot has been chosen (or when asked to choose again) the button is shown for placing, straight away.
        val placingNow = enabled && CompanionPrefs.bar(this) && placing()
        if (bar != null && barPlacing != placingNow) hideBar()
        if (placingNow || (enabled && CompanionPrefs.bar(this) && session.canShow(t))) {
            showBar(placingNow)
        } else {
            hideBar()
            // Not yet time: one timer for the remainder of the start delay (cancelled when the game leaves).
            if (enabled && CompanionPrefs.bar(this) && session.pending()) handler.postDelayed(applyLater, session.waitMs(t))
        }
        if (enabled && CompanionPrefs.notification(this)) postNotification() else cancelNotification()
    }

    private fun pasteIntent() = Intent(this, SyncActivity::class.java)
        .putExtra(SyncActivity.EXTRA_WIDGET_TAP, true)
        .putExtra(SyncActivity.EXTRA_SOURCE, "companion")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    // ---- tap handling -------------------------------------------------------------------------------------------

    private fun placing() = CompanionPrefs.placing(this) || !CompanionPrefs.hasPosition(this)

    private fun onTap() {
        if (barPlacing) return
        val t = now()
        if (t - lastTapAt < DOUBLE_TAP_MS) { // double tap: hide for this session
            lastTapAt = 0
            session.dismiss()
            apply()
            return
        }
        lastTapAt = t
        startFetch()
    }

    /** One fetch at a time; a second tap while one runs only counts towards a double tap. */
    private fun startFetch() {
        if (fetching) return
        fetching = true
        badge?.visibility = View.GONE
        pulse?.cancel()
        icon?.let { v ->
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                v,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f),
            ).apply { duration = 550; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE; start() }
        }
        handler.postDelayed(fetchTimeout, FETCH_TIMEOUT_MS)
        runCatching { startActivity(pasteIntent()) }.onFailure { report(false, getString(R.string.companion_failed)) }
    }

    private fun stopLoading() {
        fetching = false
        handler.removeCallbacks(fetchTimeout)
        pulse?.cancel(); pulse = null
        icon?.apply { alpha = 1f; scaleX = 1f; scaleY = 1f }
    }

    /** Result of an import started from the button or the notification. */
    fun showResult(ok: Boolean, message: String) {
        stopLoading()
        status = (if (ok) "✓ " else "✕ ") + message
        postNotification()
        handler.removeCallbacks(clearBadge)
        val b = badge
        if (b != null) {
            b.text = if (ok) "✓" else "✕"
            (b.background as? GradientDrawable)?.setColor(if (ok) 0xFF2E9E4F.toInt() else 0xFFD13B3B.toInt())
            b.visibility = View.VISIBLE
        }
        // The button always stays; only a double tap hides it. The result mark fades after a moment.
        handler.postDelayed(clearBadge, if (ok) SUCCESS_SHOW_MS else FAIL_SHOW_MS)
    }

    // ---- overlay ------------------------------------------------------------------------------------------------

    private fun showBar(placingMode: Boolean) {
        if (bar != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val size = (BUTTON_DP * d).toInt()
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.companion_import)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.companion_paste)
        }
        val badge = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.GRAY); setStroke((1.5f * d).toInt(), Color.WHITE) }
        }
        val button = FrameLayout(this).apply {
            addView(icon, FrameLayout.LayoutParams(size, size))
            addView(badge, FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), Gravity.TOP or Gravity.END))
            setOnClickListener { onTap() }
        }
        val root: View = if (placingMode) {
            // Placing: a small "Done" note to the left of the button; drag the button above Build, then tap Done.
            val note = TextView(this).apply {
                text = getString(R.string.companion_place_done)
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
                background = GradientDrawable().apply { setColor(0xE61F2A44.toInt()); cornerRadius = 14 * d }
            }
            android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(note, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = (8 * d).toInt() })
                addView(button, android.widget.LinearLayout.LayoutParams(size, size))
                note.setOnClickListener { finishPlacing(button) }
            }
        } else button
        // Real screen coordinates (cutout mode below). The spot is the button's centre: its distance from the right edge
        // in screen heights (the game scales its HUD by height) and its height fraction; see CompanionPrefs.
        val bounds: android.graphics.Rect = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val (rightOff, yFrac) = CompanionPrefs.position(this)
        val cx = (bounds.width() - bounds.height() * rightOff).toInt()
        val cy = (bounds.height() * yFrac).toInt()
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val rootW = root.measuredWidth.coerceAtLeast(size)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Without this the window's origin is after the notch, which pushed the button off the right edge.
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            x = (cx - size / 2 - (rootW - size)).coerceIn(0, (bounds.width() - rootW).coerceAtLeast(0))
            y = (cy - size / 2).coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
        }
        if (placingMode) {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            button.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; false }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(e.rawX - sx) > 8 * d || kotlin.math.abs(e.rawY - sy) > 8 * d) moved = true
                        if (moved) {
                            lp.x = (ox + (e.rawX - sx)).toInt().coerceIn(0, (bounds.width() - rootW).coerceAtLeast(0))
                            lp.y = (oy + (e.rawY - sy)).toInt().coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
                            runCatching { wm.updateViewLayout(root, lp) }
                        }
                        moved
                    }
                    else -> moved
                }
            }
        }
        runCatching { wm.addView(root, lp); bar = root; barPlacing = placingMode; this.icon = icon; this.badge = badge }
        if (fetching) startFetchAnimationOnly()
    }

    /** Saves where the button was left (its centre) and switches to the normal button. */
    private fun finishPlacing(button: View) {
        val loc = IntArray(2)
        button.getLocationOnScreen(loc)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else android.util.DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        val cx = loc[0] + button.width / 2f
        val cy = loc[1] + button.height / 2f
        CompanionPrefs.setPosition(this, (w - cx) / h, cy / h)
        CompanionPrefs.setPlacing(this, false)
        hideBar()
        apply()
    }

    private fun startFetchAnimationOnly() { // the overlay was re-created (rotation) during a fetch
        val v = icon ?: return
        pulse?.cancel()
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            v, PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f),
        ).apply { duration = 550; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE; start() }
    }

    private fun hideBar() {
        val v = bar ?: return
        pulse?.cancel(); pulse = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        bar = null; icon = null; badge = null; barPlacing = false
    }

    // ---- notification (unchanged behaviour) ----------------------------------------------------------------------

    private fun postNotification() {
        if (!Notifier.allowed(this) || !session.active || !CompanionPrefs.enabled(this) || !CompanionPrefs.notification(this)) return
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

    override fun onInterrupt() {
        // Another accessibility feature took over: drop any in-flight state and the overlay; the next event restores it.
        stopLoading(); hideBar()
    }

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
        stopLoading(); hideBar(); cancelNotification()
        session.onGameLeft(now())
        if (instance === this) instance = null
    }

    companion object {
        private const val CHANNEL = "companion"
        private const val NOTIFICATION_ID = 7001
        private const val BUTTON_DP = 56
        private const val DOUBLE_TAP_MS = 350L
        private const val FETCH_TIMEOUT_MS = 12_000L
        private const val SUCCESS_SHOW_MS = 1_200L
        private const val FAIL_SHOW_MS = 2_500L

        @Volatile var instance: CompanionService? = null
            private set

        /** Called after a setting changes. */
        fun refresh() { instance?.let { s -> s.handler.post { s.apply() } } }

        /** Called by the paste screen when an import from the companion finished. */
        fun report(ok: Boolean, message: String) { instance?.let { s -> s.handler.post { s.showResult(ok, message) } } }
    }
}
