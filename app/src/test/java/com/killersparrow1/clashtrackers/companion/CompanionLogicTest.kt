package com.killersparrow1.clashtrackers.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLogicTest {
    private val own = "com.killersparrow1.clashtrackers"
    private val kb = setOf("com.google.android.inputmethod.latin")

    @Test fun gameComingToFrontActivates() =
        assertTrue(CompanionLogic.next(CompanionLogic.GAME_PACKAGE, false, own, kb))

    @Test fun otherAppDeactivates() =
        assertFalse(CompanionLogic.next("com.whatsapp", true, own, kb))

    @Test fun ownPasteScreenSystemUiAndKeyboardKeepState() {
        for (p in listOf(own, "com.android.systemui", "android", "com.google.android.inputmethod.latin")) {
            assertTrue(CompanionLogic.next(p, true, own, kb))
            assertFalse(CompanionLogic.next(p, false, own, kb))
        }
    }

    @Test fun nullPackageKeepsState() = assertTrue(CompanionLogic.next(null, true, own, kb))
}
