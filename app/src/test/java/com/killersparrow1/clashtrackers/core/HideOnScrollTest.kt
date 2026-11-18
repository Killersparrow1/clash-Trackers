package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideOnScrollTest {
    private fun bar() = HideOnScroll(hideAfter = 12f, showAfter = 6f, nearTop = 24f)

    @Test fun startsVisibleAndStaysVisibleNearTheTop() {
        val b = bar()
        assertTrue(b.visible)
        assertTrue(b.onScroll(100f, distanceFromTop = 10f))
    }

    @Test fun hidesOnlyAfterTheThresholdOfDownwardScroll() {
        val b = bar()
        assertTrue(b.onScroll(5f, 500f))
        assertTrue(b.onScroll(5f, 505f))   // 10 so far
        assertFalse(b.onScroll(5f, 510f))  // 15 >= 12
    }

    @Test fun anyRealUpwardScrollRevealsIt() {
        val b = bar()
        b.onScroll(40f, 500f)
        assertFalse(b.visible)
        assertTrue(b.onScroll(-8f, 492f))  // 8 >= 6
    }

    @Test fun smallJitterDoesNotFlicker() {
        val b = bar()
        b.onScroll(40f, 500f)
        assertFalse(b.visible)
        // up 3, down 3, up 3, down 3: neither side ever reaches its threshold in one direction
        repeat(5) {
            assertFalse(b.onScroll(-3f, 497f))
            assertFalse(b.onScroll(3f, 500f))
        }
    }

    @Test fun reversingDirectionRestartsTheCount() {
        val b = bar()
        b.onScroll(10f, 500f)               // 10 down, not yet hidden
        b.onScroll(-1f, 499f)               // direction change clears the downward count
        assertTrue(b.onScroll(10f, 509f))   // 10 again, still under 12
    }

    @Test fun scrollingBackToTheTopShowsItAndTapsForceItVisible() {
        val b = bar()
        b.onScroll(40f, 500f)
        assertFalse(b.visible)
        assertTrue(b.onScroll(-1f, 10f))   // near the top
        b.onScroll(40f, 500f)
        b.show()
        assertTrue(b.visible)
        assertTrue(b.onScroll(5f, 500f))   // the old count was forgotten
    }
}
