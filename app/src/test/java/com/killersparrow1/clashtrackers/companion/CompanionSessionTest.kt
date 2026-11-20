package com.killersparrow1.clashtrackers.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSessionTest {
    @Test fun waitsForTheStartDelay() {
        val s = CompanionSession()
        s.onGameEntered(1_000)
        assertEquals(6_250L, s.waitMs(1_000))
        assertFalse(s.canShow(5_000))
        assertTrue(s.canShow(7_250))
    }

    @Test fun briefAbsenceDoesNotRestartTheTimer() {
        val s = CompanionSession()
        s.onGameEntered(0)
        s.onGameLeft(3_000)
        s.onGameEntered(10_000)           // 7 s away: same session, delay already over
        assertTrue(s.canShow(10_000))
    }

    @Test fun dismissalAndUseLastForTheSessionOnly() {
        val s = CompanionSession()
        s.onGameEntered(0)
        s.dismiss()
        assertFalse(s.canShow(10_000))
        s.onGameLeft(11_000); s.onGameEntered(20_000)  // short absence: still dismissed
        assertFalse(s.canShow(30_000))
        s.onGameLeft(31_000); s.onGameEntered(31_000 + 60_000) // long absence: fresh session
        assertFalse(s.dismissed)
        assertFalse(s.canShow(31_000 + 60_000))        // delay applies again
        assertTrue(s.canShow(31_000 + 60_000 + 6_250))
        s.markUsed()
        assertFalse(s.canShow(200_000))
        assertFalse(s.pending())
    }

    @Test fun hiddenWhenGameNotActive() {
        val s = CompanionSession()
        s.onGameEntered(0)
        s.onGameLeft(8_000)
        assertFalse(s.canShow(9_000))
        assertFalse(s.pending())
    }
}
