package com.killersparrow1.clashtrackers.companion

/**
 * One "game session" for the floating import button, kept free of Android types so it can be unit tested.
 *
 * Android does not say whether Clash of Clans was cold-started or just resumed, so a session starts when the game comes
 * to the front after being away for at least [freshGapMs] (or for the first time). A shorter absence, such as a
 * notification shade, a keyboard or a quick app switch, continues the same session: the start delay is not restarted
 * and a dismissal or a successful import still applies.
 * All times are a monotonic clock in milliseconds.
 */
class CompanionSession(
    private val delayMs: Long = START_DELAY_MS,
    private val freshGapMs: Long = FRESH_GAP_MS,
) {
    var active = false
        private set
    private var startedAt: Long? = null
    private var leftAt: Long? = null
    var dismissed = false
        private set
    var used = false
        private set

    fun onGameEntered(now: Long) {
        val left = leftAt
        if (startedAt == null || (left != null && now - left >= freshGapMs)) {
            startedAt = now
            dismissed = false
            used = false
        }
        leftAt = null
        active = true
    }

    fun onGameLeft(now: Long) {
        if (!active) return
        active = false
        leftAt = now
    }

    /** Manual double-tap dismissal: lasts for this session only. */
    fun dismiss() { dismissed = true }

    /** A successful import: the button stays away for the rest of this session. */
    fun markUsed() { used = true }

    /** Milliseconds until the button may show, 0 when it may show now. */
    fun waitMs(now: Long): Long {
        val start = startedAt ?: return delayMs
        return (start + delayMs - now).coerceAtLeast(0)
    }

    fun canShow(now: Long): Boolean = active && !dismissed && !used && waitMs(now) == 0L

    /** True while the button could still appear later in this session (used to decide whether to schedule it). */
    fun pending(): Boolean = active && !dismissed && !used

    companion object {
        /** About how long the home village takes to load after the game opens. */
        const val START_DELAY_MS = 6_250L
        const val FRESH_GAP_MS = 60_000L
    }
}
