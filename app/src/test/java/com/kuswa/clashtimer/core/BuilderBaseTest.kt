package com.kuswa.clashtimer.core

import com.kuswa.clashtimer.core.SampleExports.export1
import com.kuswa.clashtimer.core.SampleExports.export3
import com.kuswa.clashtimer.core.SampleExports.names
import com.kuswa.clashtimer.core.SampleExports.times
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builder Base (night village) support, using the real timer in `export_3.json` (`buildings2`). */
class BuilderBaseTest {

    @Test fun parsesBuilderBaseBuildingTimer() {
        val job = export3.bbBuilderJobs.single()
        assertEquals(1000041, job.dataId) // Double Cannon
        assertEquals(7, job.currentLevel)
        assertEquals(90808L, job.remainingAtT0)
        assertEquals(JobSource.BB_BUILDING, job.source)
        assertEquals(JobCategory.BB_BUILDER, job.category)
        assertTrue(export3.starLabJobs.isEmpty())
    }

    @Test fun homeVillageCountsAreUnchanged() {
        assertEquals(7, export3.builderJobs.size)
        assertEquals(6, export3.regularBuilderJobs.size)
        assertFalse(export3.bbBuilderJobs.isEmpty())
        assertTrue(export1.bbBuilderJobs.isEmpty()) // export 1 has nothing running in the night village
    }

    @Test fun nameAndFullTimeResolve() {
        val job = export3.bbBuilderJobs.single()
        assertEquals("Double Cannon → 8", names.label(job))
        assertNotNull(times.fullTime(job))
        assertTrue(times.fullTime(job)!! >= job.remainingAtT0)
    }

    @Test fun noHelpersOrPotionsApplyInBuilderBase() {
        val job = export3.bbBuilderJobs.single()
        val state = AppState(exportJson = SampleExports.text("export_3.json"))
        // A builder potion is running for all of the time left, plus a helper is linked on a home job.
        val potion = listOf(BoostUse(BoostType.BUILDER_POTION, export3.timestamp, export3.timestamp + 10 * 3600))
        val engine = TimerEngine()
        assertEquals(
            export3.timestamp + job.remainingAtT0,
            engine.finishTime(job, export3, state.links, potion),
        )
    }

    @Test fun widgetShowsBuilderBaseSectionOnlyWhenBusy() {
        val busy = WidgetModelBuilder.build(
            AppState(exportJson = SampleExports.text("export_3.json")), AppSettings(), names, times, export3.timestamp,
        )
        assertTrue(busy.showBuilderBase)
        assertEquals(listOf("Double Cannon"), busy.bbRows.map { it.name })
        assertEquals("8", busy.bbRows.single().levelText)
        assertNull(busy.starLabRow)

        val idle = WidgetModelBuilder.build(
            AppState(exportJson = SampleExports.text("export_1.json")), AppSettings(), names, times, export1.timestamp,
        )
        assertFalse(idle.showBuilderBase)
    }

    @Test fun finishNoticeNamesTheBuilderBaseBuilder() {
        val n = FinishNotices.notice(export3.bbBuilderJobs.single(), names)
        assertEquals("Builder Base builder free: Double Cannon", n.title)
    }
}
