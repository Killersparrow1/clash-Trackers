package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAccountTest {

    private val text1 = SampleExports.text("export_1.json")
    private val text2 = SampleExports.text("export_2.json")

    @Test
    fun testTagNormalization() {
        assertEquals("#ABC123", normalizeTag("abc123"))
        assertEquals("#ABC123", normalizeTag("#abc123"))
        assertEquals("#TAG", normalizeTag("  tag  "))
    }

    @Test
    fun testMultiAccountSyncLogic() {
        val o1 = SyncLogic.apply(AppState(), text1)
        assertTrue(o1 is SyncLogic.Outcome.Synced)
        val s1 = (o1 as SyncLogic.Outcome.Synced).state
        val export1 = o1.export

        val o2 = SyncLogic.apply(AppState(), text2)
        assertTrue(o2 is SyncLogic.Outcome.Synced)
        val s2 = (o2 as SyncLogic.Outcome.Synced).state
        val export2 = o2.export

        // Different accounts have separate states
        assertEquals(export1.tag, s1.export!!.tag)
        assertEquals(export2.tag, s2.export!!.tag)
    }

    @Test
    fun testNotifiedSetPerAccount() {
        val s1 = AppState(notified = setOf("job1"))
        val s2 = AppState(notified = setOf("job2"))
        assertEquals(setOf("job1"), s1.notified)
        assertEquals(setOf("job2"), s2.notified)
    }
}
