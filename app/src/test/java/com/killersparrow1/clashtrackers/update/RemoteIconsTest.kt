package com.killersparrow1.clashtrackers.update

import com.killersparrow1.clashtrackers.core.MaxData
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class RemoteIconsTest {
    @Test
    fun acceptsPlainAssetPaths() {
        assertTrue(RemoteIcons.safePath("buildings/home-village/spiky_tower/level_2.webp"))
        assertTrue(RemoteIcons.safePath("troops/barch_squad/icon.webp"))
        assertTrue(RemoteIcons.safePath("spells/heroic_spell.webp"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertFalse(RemoteIcons.safePath(""))
        assertFalse(RemoteIcons.safePath("../secret.webp"))
        assertFalse(RemoteIcons.safePath("/abs/icon.webp"))
        assertFalse(RemoteIcons.safePath("https://evil.example/icon.webp"))
        assertFalse(RemoteIcons.safePath("troops/a b/icon.webp"))
        assertFalse(RemoteIcons.safePath("troops/barbarian/icon.png"))
    }

    @Test
    fun readsNameAndIconFromGameData() {
        val json = """{"maxTh":19,"items":{"1000107":{"k":"b","c":"d","n":null,"l":[[10,100,0,19]],"nm":"Spiky Tower","i":"buildings/home-village/spiky_tower/level_1.webp"},"1000001":{"k":"b","c":"d","n":null,"l":[[10,100,0,1]]}}}"""
        val data = MaxData.fromJson(json)
        assertEquals("Spiky Tower", data.items[1000107]?.name)
        assertEquals("buildings/home-village/spiky_tower/level_1.webp", data.items[1000107]?.icon)
        assertEquals(null, data.items[1000001]?.name)
    }
}
