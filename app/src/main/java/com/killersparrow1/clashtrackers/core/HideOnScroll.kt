package com.killersparrow1.clashtrackers.core

/**
 * Decides when the floating tab bar hides. Pure logic, no UI: the caller feeds it the scroll movement of the content.
 *
 *  - hides after [hideAfter] of continuous downward scrolling (toward the end of a list);
 *  - shows again after [showAfter] of continuous upward scrolling, so any real upward scroll reveals it but a
 *    one-pixel jitter does not (the two thresholds differ on purpose: hysteresis);
 *  - always visible within [nearTop] of the top of the content.
 * Distances are in the same unit (pixels).
 */
class HideOnScroll(
    private val hideAfter: Float,
    private val showAfter: Float,
    private val nearTop: Float,
) {
    var visible: Boolean = true
        private set
    private var down = 0f
    private var up = 0f

    /**
     * [dy] is how far the content moved toward its end (positive = scrolling down, negative = up);
     * [distanceFromTop] is how far the content is scrolled from its top. Returns whether the bar is visible.
     */
    fun onScroll(dy: Float, distanceFromTop: Float): Boolean {
        if (distanceFromTop <= nearTop) {
            show()
            return visible
        }
        when {
            dy > 0f -> {
                down += dy
                up = 0f
                if (down >= hideAfter) visible = false
            }
            dy < 0f -> {
                up -= dy
                down = 0f
                if (up >= showAfter) visible = true
            }
        }
        return visible
    }

    /** Visible again and forget the movement so far (a tab was tapped, scrolling stopped, a page change started). */
    fun show() {
        visible = true
        down = 0f
        up = 0f
    }
}
