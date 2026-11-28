package com.killersparrow1.clashtrackers.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Notices when scrolling with the blur on is choppy and suggests turning the blur off, once. It only suggests: the person
 * chooses. The answer is kept, so it is never asked again (the blur can still be changed in Settings > Personalization).
 */
object ScrollBlurAdvice {
    private const val PREFS = "scroll_blur"
    private const val KEY = "advice_done"

    /** True while the "turn it off?" question should be on screen. */
    var suggest by mutableStateOf(false)
        private set

    private var done = false
    private val meter = JankMeter()

    fun init(context: Context) {
        done = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    /** Whether frames should still be measured. */
    internal val measuring: Boolean get() = !done && !suggest

    /** One frame of a moving list took [dtMs]; the screen refreshes every [refreshMs]. */
    internal fun onFrame(dtMs: Float, refreshMs: Float) {
        if (measuring) meter.frame(dtMs, refreshMs)
    }

    /** The list stopped moving: a good moment to ask, never in the middle of a scroll. */
    internal fun onScrollEnd() {
        if (!measuring) return
        meter.endSession()
        if (meter.shouldSuggest()) suggest = true
    }

    /** The person answered (either way): do not ask again. */
    fun answered(context: Context) {
        done = true
        suggest = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    /** The blur setting changed: start measuring from scratch. */
    internal fun reset() = meter.reset()
}

/** Counts slow frames over a few scrolls. Plain Kotlin so it can be tested. */
internal class JankMeter {
    private class Session(val frames: Int, val slow: Int)

    private val sessions = ArrayDeque<Session>()
    private var frames = 0
    private var slow = 0

    fun frame(dtMs: Float, refreshMs: Float) {
        // A pause of a quarter of a second or more is the app being away, not scrolling.
        if (dtMs <= 0f || dtMs > 250f) return
        frames++
        if (dtMs > refreshMs * SLOW_FACTOR) slow++
    }

    fun endSession() {
        // Very short scrolls say little; keep the last few longer ones.
        if (frames >= MIN_FRAMES) {
            sessions.addLast(Session(frames, slow))
            while (sessions.size > MAX_SESSIONS) sessions.removeFirst()
        }
        frames = 0
        slow = 0
    }

    fun shouldSuggest(): Boolean {
        if (sessions.size < MIN_SESSIONS) return false
        val total = sessions.sumOf { it.frames }
        val slowTotal = sessions.sumOf { it.slow }
        return total > 0 && slowTotal.toFloat() / total >= SLOW_SHARE
    }

    fun reset() {
        sessions.clear()
        frames = 0
        slow = 0
    }

    companion object {
        /** A frame counts as slow when it took more than this many screen refreshes. */
        const val SLOW_FACTOR = 1.75f
        const val MIN_FRAMES = 20
        const val MIN_SESSIONS = 3
        const val MAX_SESSIONS = 5
        /** One slow frame in five or worse. */
        const val SLOW_SHARE = 0.2f
    }
}
