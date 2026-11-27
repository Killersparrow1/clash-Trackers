package com.killersparrow1.clashtrackers.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityReminderTest {
    private val now = 1_000_000_000_000L

    @Test
    fun asksByDefault() {
        assertTrue(AccessibilityReminder.allowed(never = false, snoozedUntil = 0L, now = now))
    }

    @Test
    fun remindMeLaterPausesForAWeekThenAsksAgain() {
        val until = AccessibilityReminder.snoozeEnd(now)
        assertEquals(7L * 24 * 3_600_000, until - now)
        assertFalse(AccessibilityReminder.allowed(false, until, now + 1))
        assertFalse(AccessibilityReminder.allowed(false, until, until - 1))
        assertTrue(AccessibilityReminder.allowed(false, until, until))
    }

    @Test
    fun dontAskAgainNeverAsksEvenAfterTheSnoozeEnded() {
        assertFalse(AccessibilityReminder.allowed(true, 0L, now))
        assertFalse(AccessibilityReminder.allowed(true, now - 1, now))
    }
}
