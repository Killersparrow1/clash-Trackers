package com.killersparrow1.clashtrackers.core

import com.killersparrow1.clashtrackers.core.SampleExports.export1
import com.killersparrow1.clashtrackers.core.SampleExports.export3
import com.killersparrow1.clashtrackers.core.SampleExports.names
import com.killersparrow1.clashtrackers.core.SampleExports.times
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
        // The Star Laboratory is free here: it gets an idle row like the Laboratory does, with its own building picture.
        val starLab = busy.starLabRow!!
        assertEquals(RowKind.IDLE, starLab.kind)
        assertEquals("Star Laboratory", starLab.name)
        assertEquals(listOf(GameIds.STAR_LABORATORY), starLab.iconIds)
        assertEquals(RowKind.IDLE, busy.starLabRow!!.kind)

        val idle = WidgetModelBuilder.build(
            AppState(exportJson = SampleExports.text("export_1.json")), AppSettings(), names, times, export1.timestamp,
        )
        assertFalse(idle.showBuilderBase)
    }

    @Test fun freeStarLaboratoryIsNotABuilderBaseSection() {
        // Nothing is running in the night village, so the widget's Builder Base section stays hidden even though the idle row exists.
        val idle = WidgetModelBuilder.build(
            AppState(exportJson = SampleExports.text("export_1.json")), AppSettings(), names, times, export1.timestamp,
        )
        assertEquals(RowKind.IDLE, idle.starLabRow?.kind)
        assertFalse(idle.showBuilderBase)
    }

    @Test fun starLaboratoryLevelAndPresenceComeFromTheExport() {
        fun parse(json: String) = (ExportParser.parse(json) as ParseResult.Success).export
        val withBuilding = parse("""{"tag":"#A","timestamp":1,"buildings":[{"data":1000001,"lvl":17},{"data":1000007,"lvl":13}],"buildings2":[{"data":1000046,"lvl":8}]}""")
        assertTrue(withBuilding.hasStarLab)
        assertEquals(8, withBuilding.starLabLevel)
        assertEquals(13, withBuilding.labLevel)
        val withTroops = parse("""{"tag":"#A","timestamp":1,"buildings":[{"data":1000001,"lvl":17}],"units2":[{"data":4000031,"lvl":5}]}""")
        assertTrue(withTroops.hasStarLab)
        assertNull(withTroops.starLabLevel)
        val none = parse("""{"tag":"#A","timestamp":1,"buildings":[{"data":1000001,"lvl":17}]}""")
        assertFalse(none.hasStarLab)
    }

    @Test fun finishNoticeNamesTheBuilderBaseBuilder() {
        val n = FinishNotices.notice(export3.bbBuilderJobs.single(), names)
        assertEquals("Builder Base builder free: Double Cannon", n.title)
    }

    @Test fun builderBaseAlertsCanBeSwitchedOffWithoutLosingHomeAlerts() {
        val state = AppState(exportJson = SampleExports.text("export_3.json"))
        val late = export3.timestamp + 90_808L + 60 // just after the Builder Base builder finishes
        val all = FinishNotices.check(state, AppSettings(), names, late)
        assertTrue(all.notices.any { it.title.startsWith("Builder Base builder free") })

        val homeOnly = FinishNotices.check(state, AppSettings(), names, late, builderBaseAlerts = false)
        assertTrue(homeOnly.notices.none { it.title.startsWith("Builder Base") })
        assertEquals(all.notices.size - 1, homeOnly.notices.size)
        // Switched off is still "announced": turning it on later does not replay the old finish.
        assertEquals(all.notified, homeOnly.notified)

        val bbOnly = FinishNotices.check(state, AppSettings(), names, late, homeAlerts = false)
        assertTrue(bbOnly.notices.all { it.title.startsWith("Builder Base") })
    }
}
