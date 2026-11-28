package com.killersparrow1.clashtrackers.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollBlurTest {
    @Test fun speedFollowsTheMovementAndIsSmoothed() {
        // 30 px in a 15 ms frame is 2 px/ms; from rest the speed moves 65% of the way there.
        assertEquals(1.3f, ScrollBlurMath.nextSpeed(0f, 30f, 15f, moving = true), 0.001f)
        assertTrue(ScrollBlurMath.nextSpeed(1.3f, 30f, 15f, moving = true) > 1.3f)
    }

    @Test fun speedKeepsItsDirection() {
        assertTrue(ScrollBlurMath.nextSpeed(0f, -30f, 15f, moving = true) < 0f)
    }

    @Test fun speedIsCapped() {
        val s = ScrollBlurMath.nextSpeed(ScrollBlurMath.MAX_SPEED, 10_000f, 10f, moving = true)
        assertTrue(s <= ScrollBlurMath.MAX_SPEED + 0.0001f)
    }

    @Test fun speedFadesOutOnceTheScrollStops() {
        var s = 2f
        var frames = 0
        while (s != 0f && frames < 20) { s = ScrollBlurMath.nextSpeed(s, 0f, 16f, moving = false); frames++ }
        assertEquals(0f, s, 0f)
        assertTrue("fades in a handful of frames", frames <= 8)
    }

    @Test fun oddFrameLengthsResetTheSpeed() {
        assertEquals(0f, ScrollBlurMath.nextSpeed(2f, 30f, 0.2f, moving = true), 0f)
        assertEquals(0f, ScrollBlurMath.nextSpeed(2f, 30f, 400f, moving = true), 0f)
    }

    @Test fun blurLengthIsLimitedAndScaledByTheLevel() {
        assertEquals(60f, ScrollBlurMath.blurPx(3f, 1f), 0f)
        assertEquals(-60f, ScrollBlurMath.blurPx(-3f, 1f), 0f)
        assertEquals(11f, ScrollBlurMath.blurPx(1f, 0.5f), 0.001f)
        assertEquals(0f, ScrollBlurMath.blurPx(2f, ScrollBlurSetting.scaleFor(ScrollBlurSetting.OFF)), 0f)
        assertTrue(ScrollBlurSetting.scaleFor(ScrollBlurSetting.HIGH) > ScrollBlurSetting.scaleFor(ScrollBlurSetting.LOW))
    }
}
