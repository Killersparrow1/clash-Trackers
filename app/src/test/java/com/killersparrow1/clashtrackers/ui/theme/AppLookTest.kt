package com.killersparrow1.clashtrackers.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLookTest {
    @Test fun keysAreUniqueAndRoundTrip() {
        assertEquals(AppLook.entries.size, AppLook.entries.map { it.key }.toSet().size)
        for (look in AppLook.entries) assertEquals(look, AppLook.fromKey(look.key))
    }

    @Test fun unknownOrMissingKeyFallsBackToMaterialYou() {
        assertEquals(AppLook.MATERIAL_YOU, AppLook.fromKey(null))
        assertEquals(AppLook.MATERIAL_YOU, AppLook.fromKey("wallpaper"))
    }

    @Test fun everyLookHasThreeSwatchColours() {
        assertTrue(AppLook.entries.all { it.swatch.size == 3 })
    }

    @Test fun lightIsAlwaysLightAndDarkIsAlwaysDark() {
        assertEquals(false, AppLook.MATERIAL_LIGHT.dark)
        assertEquals(true, AppLook.MATERIAL_DARK.dark)
    }

    @Test fun onlyMaterialYouFollowsThePhone() {
        assertEquals(listOf(AppLook.MATERIAL_YOU), AppLook.entries.filter { it.dark == null })
    }
}
