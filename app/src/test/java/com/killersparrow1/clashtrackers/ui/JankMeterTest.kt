package com.killersparrow1.clashtrackers.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JankMeterTest {
    private val refresh = 16.7f

    private fun JankMeter.scroll(frames: Int, slowEvery: Int) {
        repeat(frames) { i -> frame(if (slowEvery > 0 && i % slowEvery == 0) 40f else refresh, refresh) }
        endSession()
    }

    @Test fun smoothScrollsNeverSuggest() {
        val m = JankMeter()
        repeat(5) { m.scroll(60, 0) }
        assertFalse(m.shouldSuggest())
    }

    @Test fun needsSeveralScrolls() {
        val m = JankMeter()
        repeat(2) { m.scroll(60, 2) }
        assertFalse(m.shouldSuggest())
        m.scroll(60, 2)
        assertTrue(m.shouldSuggest())
    }

    @Test fun shortScrollsAreIgnored() {
        val m = JankMeter()
        repeat(5) { m.scroll(10, 1) }
        assertFalse(m.shouldSuggest())
    }

    @Test fun longPausesAreIgnored() {
        val m = JankMeter()
        repeat(3) {
            repeat(30) { m.frame(refresh, refresh) }
            repeat(10) { m.frame(400f, refresh) }
            m.endSession()
        }
        assertFalse(m.shouldSuggest())
    }

    @Test fun resetForgets() {
        val m = JankMeter()
        repeat(3) { m.scroll(60, 2) }
        m.reset()
        assertFalse(m.shouldSuggest())
    }
}
