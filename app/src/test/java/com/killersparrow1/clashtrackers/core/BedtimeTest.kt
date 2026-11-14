package com.killersparrow1.clashtrackers.core

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeTest {
    private val utc = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0, day: Int = 14) = LocalDateTime.of(2026, 11, day, h, m).toEpochSecond(ZoneOffset.UTC)
    private val sleep = SleepWindow(23 * 60, 7 * 60)
    private val hour = 3600L

    // Item 1 (building): steps TO level 1..4 take 6 h, 12 h, 14 h, 100 h. Item 2 (hero): 12 h then 16 h. Item 3 (lab): level 2 takes 11.5 h.
    private val data = MaxData.fromJson(
        """{"maxTh":2,"items":{
          "1":{"k":"b","n":null,"l":[[21600,10,0,1],[43200,20,0,1],[50400,30,0,1],[360000,40,0,1]]},
          "2":{"k":"h","n":null,"l":[[43200,5,2,1],[57600,6,2,1]]},
          "3":{"k":"l","n":null,"l":[[0,0,1,1],[41400,7,1,1]]}
        }}""",
    )

    private fun export(owned: Map<Int, List<Int>>, jobs: List<Job> = emptyList()) = VillageExport(
        tag = "#T", timestamp = 0, townHallLevel = 1, builderHutCount = 5, helpers = emptyList(), jobs = jobs, ownedLevels = owned,
    )

    @Test fun sleepWindowKnowsWhenYouAreAsleep() {
        assertTrue(sleep.asleep(at(23, 30), utc))
        assertTrue(sleep.asleep(at(3), utc))
        assertFalse(sleep.asleep(at(7), utc))
        assertFalse(sleep.asleep(at(22, 59), utc))
        assertFalse(SleepWindow(600, 600).asleep(at(3), utc))
        assertEquals(at(7, 0, 15), sleep.nextAwake(at(23, 30), utc))
        assertEquals(at(19, 44), sleep.nextAwake(at(19, 44), utc))
    }

    @Test fun keepsOnlyUpgradesThatRunThroughTheNightAndEndAwake() {
        // Slot free 7:44 pm. Wake is 7:00 am next day (11 h 16 m later).
        val plan = Bedtime.plan(data, export(mapOf(1 to listOf(1), 2 to listOf(1))), PlanSlot.BUILDER, at(19, 44), sleep, utc)!!
        // Building 1 -> 2 takes 12 h (done 7:44 am) and hero 1 -> 2 takes 16 h (done 11:44 am): both in.
        val keys = plan.items.map { it.id to it.level }
        assertTrue((1 to 2) in keys)
        assertTrue((2 to 2) in keys)
        assertEquals(at(7, 44, 15), plan.items.first().finishAt)
        assertEquals(at(7, 0, 15), plan.wakeAt)
    }

    @Test fun dropsUpgradesThatWouldFinishWhileAsleep() {
        // Level 3 -> 4 takes 100 h from 7:44 pm: ends 11:44 pm four days on, asleep. Level 2 -> 3 (14 h) ends 9:44 am.
        val plan = Bedtime.plan(data, export(mapOf(1 to listOf(2))), PlanSlot.BUILDER, at(19, 44), sleep, utc)!!
        assertEquals(listOf(3), plan.items.map { it.level })
        val long = Bedtime.plan(data, export(mapOf(1 to listOf(3))), PlanSlot.BUILDER, at(19, 44), sleep, utc)!!
        assertTrue(long.items.isEmpty())
    }

    @Test fun anUpgradeAlreadyRunningIsNotOffered() {
        // The export lists the building at level 2 because it is being upgraded to 2 (job target level 2).
        val job = Job(JobCategory.BUILDER, JobSource.BUILDING, 1, 1, 100, false)
        val plan = Bedtime.plan(data, export(mapOf(1 to listOf(2)), listOf(job)), PlanSlot.BUILDER, at(19, 44), sleep, utc)!!
        assertTrue(plan.items.none { it.id == 1 })
    }

    @Test fun slotsOnlyListTheirOwnKind() {
        val e = export(mapOf(1 to listOf(1), 3 to listOf(1)))
        assertTrue(Bedtime.plan(data, e, PlanSlot.LAB, at(19, 44), sleep, utc)!!.items.all { it.id == 3 })
        assertTrue(Bedtime.plan(data, e, PlanSlot.PET, at(19, 44), sleep, utc)!!.items.isEmpty())
    }

    @Test fun aSlotThatFreesWhileYouSleepStartsInTheMorning() {
        // Free at 2 am: starts 7 am, so 12 h is done 7 pm (awake) but the next sleep starts at 11 pm and the wake is next morning: not enough.
        val plan = Bedtime.plan(data, export(mapOf(1 to listOf(1))), PlanSlot.BUILDER, at(2, 0), sleep, utc)!!
        assertEquals(at(7, 0), plan.startAt)
        assertTrue(plan.items.isEmpty())
    }

    @Test fun durationLabelIsShort() {
        assertEquals("12h", Bedtime.durationLabel(12 * hour))
        assertEquals("14h 30m", Bedtime.durationLabel(14 * hour + 1800))
        assertEquals("2d 4h", Bedtime.durationLabel(52 * hour))
        assertEquals("2d", Bedtime.durationLabel(48 * hour))
    }
}
