package com.killersparrow1.clashtrackers.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun versionsCompareNumberByNumber() {
        assertTrue(UpdateCheck.isNewer("2.8", "CT2.9"))
        assertTrue(UpdateCheck.isNewer("2.9", "CT2.10"))
        assertTrue(UpdateCheck.isNewer("2.9", "CT3.0"))
        assertTrue(UpdateCheck.isNewer("2.8", "2.8.1"))
        assertFalse(UpdateCheck.isNewer("2.8", "CT2.8"))
        assertFalse(UpdateCheck.isNewer("3.0", "CT2.9"))
        assertFalse(UpdateCheck.isNewer("2.8", "nightly"))
    }

    @Test fun readsTheApkFromTheLatestRelease() {
        val json = """{"tag_name":"CT2.9","draft":false,"body":" Fixes things. ","assets":[
            {"name":"notes.txt","browser_download_url":"https://github.com/x/y/releases/download/CT2.9/notes.txt","size":10},
            {"name":"ClashTrackers-CT2.9.apk","browser_download_url":"https://github.com/x/y/releases/download/CT2.9/ClashTrackers-CT2.9.apk","size":9000000}]}"""
        val info = UpdateCheck.parseLatest(json)
        assertNotNull(info)
        assertEquals("2.9", info!!.version)
        assertEquals("CT2.9", info.tag)
        assertEquals("ClashTrackers-CT2.9.apk", info.apkName)
        assertEquals(9_000_000L, info.apkSize)
        assertEquals("Fixes things.", info.notes)
    }

    @Test fun refusesDownloadsFromOtherHostsAndReleasesWithoutAnApk() {
        val elsewhere = """{"tag_name":"CT2.9","assets":[{"name":"a.apk","browser_download_url":"https://evil.example/a.apk","size":1}]}"""
        assertNull(UpdateCheck.parseLatest(elsewhere))
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"CT2.9","assets":[]}"""))
        assertNull(UpdateCheck.parseLatest("not json"))
    }
}
