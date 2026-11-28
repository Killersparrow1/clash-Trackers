package com.killersparrow1.clashtrackers.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollBlurTest {
    @Test fun offNeverBlurs() {
        assertEquals(0f, ScrollBlurSetting.radiusFor(500f, ScrollBlurSetting.OFF), 0f)
    }

    @Test fun slowScrollingIsBarelyBlurredAndFastScrollingIsCapped() {
        assertTrue(ScrollBlurSetting.radiusFor(2f, ScrollBlurSetting.LOW) < 0.5f)
        assertEquals(8f, ScrollBlurSetting.radiusFor(10_000f, ScrollBlurSetting.LOW), 0f)
        assertEquals(16f, ScrollBlurSetting.radiusFor(10_000f, ScrollBlurSetting.HIGH), 0f)
    }

    @Test fun highBlursMoreThanLow() {
        assertTrue(ScrollBlurSetting.radiusFor(60f, ScrollBlurSetting.HIGH) > ScrollBlurSetting.radiusFor(60f, ScrollBlurSetting.LOW))
    }
}
