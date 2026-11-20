package com.killersparrow1.clashtrackers.sync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.killersparrow1.clashtrackers.data.StateRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * Invisible activity launched by the widget's Sync button.
 *
 * Android 10+ only lets the focused app read the clipboard, so the read happens in
 * [onWindowFocusChanged]. Some ROMs are slow (or never) to deliver focus to a translucent
 * window, so there is a fallback that tries anyway after [FOCUS_FALLBACK_MS] and a hard
 * timeout so this never lingers invisibly over the home screen. Every step is written to the
 * sync log shown on the launcher screen, since there is no logcat on most phones.
 */
class SyncActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private val source get() = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE

    private val focusFallback = Runnable { startSync(viaFallback = true) }
    private val hardTimeout = Runnable {
        log(false, "Sync screen timed out without finishing")
        finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a deliberate launch (the widget's button or the Sync shortcut) runs. Anything else is logged and dropped.
        // The widget button marks its launch; the app-icon "Sync" shortcut (also what Back Tap or Quick Tap can start) uses MAIN.
        if (!intent.getBooleanExtra(EXTRA_WIDGET_TAP, false) && intent.action != android.content.Intent.ACTION_MAIN) {
            log(false, "Ignored a launch that did not come from the widget's Sync button. " + launchDetails())
            started = true
            finishQuietly()
            return
        }
        // A real tap never repeats within a few seconds; an automatic or stray launch is dropped silently instead of
        // covering whatever app is open and toasting over it.
        val prefs = getSharedPreferences("sync_activity", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_open", 0L)
        prefs.edit().putLong("last_open", now).apply()
        // The companion button/notification has its own one-at-a-time guard and must always get an answer.
        if (source != "companion" && now - last in 0 until REPEAT_GUARD_MS) {
            log(false, "Ignored a repeated Sync screen launch (${now - last} ms after the last)")
            started = true
            finishQuietly()
            return
        }
        log(null, "Sync screen opened from widget. " + launchDetails())
        handler.postDelayed(hardTimeout, HARD_TIMEOUT_MS)
    }

    override fun onResume() {
        super.onResume()
        if (!started) {
            if (hasWindowFocus()) startSync(viaFallback = false)
            else handler.postDelayed(focusFallback, FOCUS_FALLBACK_MS)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) startSync(viaFallback = false)
    }

    private fun startSync(viaFallback: Boolean) {
        if (started) return
        started = true
        handler.removeCallbacks(focusFallback)

        val text = SyncRunner.readClipboard(this)
        if (viaFallback) log(null, "No window focus after ${FOCUS_FALLBACK_MS} ms; read clipboard anyway")

        lifecycleScope.launch {
            val result = SyncRunner.sync(this@SyncActivity, text, source)
            // Shown while this activity is still in front, so ROMs that block background toasts allow it.
            Toast.makeText(this@SyncActivity, result.message, Toast.LENGTH_LONG).show()
            if (source == "companion") {
                if (result.ok) com.killersparrow1.clashtrackers.sync.ClipboardAutoSync.markUsed(applicationContext, text)
                com.killersparrow1.clashtrackers.companion.CompanionService.report(result.ok, result.message)
            }
            finishQuietly()
        }
    }

    private fun log(ok: Boolean?, message: String) {
        val app = applicationContext
        // NonCancellable: breadcrumbs must survive the activity finishing right after.
        lifecycleScope.launch(NonCancellable) { StateRepository(app).log(source, ok, message) }
    }

    /** Why this screen opened: who started it, the screen state and the app that was in front (for the activity log). */
    private fun launchDetails(): String = runCatching {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val km = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val front = am.runningAppProcesses.orEmpty()
            .filter { it.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE && it.processName != packageName }
            .joinToString(",") { it.processName }.ifEmpty { "none" }
        "referrer=${referrer?.host ?: "none"}, flags=0x${Integer.toHexString(intent.flags)}, " +
            "screenOn=${pm.isInteractive}, locked=${km.isKeyguardLocked}, visibleApps=$front, android=${android.os.Build.VERSION.SDK_INT}"
    }.getOrDefault("details unavailable")

    private fun finishQuietly() {
        handler.removeCallbacksAndMessages(null)
        if (!isFinishing) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WIDGET_TAP = "widget_tap"
        const val EXTRA_SOURCE = "source"
        private const val SOURCE = "widget"
        private const val FOCUS_FALLBACK_MS = 1500L
        private const val HARD_TIMEOUT_MS = 8000L
        private const val REPEAT_GUARD_MS = 4000L
    }
}
