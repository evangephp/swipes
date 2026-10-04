package com.swipecounter.milestones

import com.swipecounter.data.DailyStatsEntity
import com.swipecounter.data.MilestoneRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MilestonesTest {
    private val today = LocalDate.of(2026, 10, 4) // a Sunday
    private val us = Locale.US

    @Test
    fun `period keys and starts`() {
        assertEquals("D:2026-10-04", Milestones.periodKey(Period.DAY, today, us))
        // US weeks start on Sunday; ISO-style locales on Monday.
        assertEquals(LocalDate.of(2026, 10, 4), Milestones.periodStart(Period.WEEK, today, us))
        assertEquals(LocalDate.of(2026, 9, 28), Milestones.periodStart(Period.WEEK, today, Locale.GERMANY))
        assertEquals("M:2026-10", Milestones.periodKey(Period.MONTH, today, us))
        assertEquals("Y:2026", Milestones.periodKey(Period.YEAR, today, us))
    }

    @Test
    fun `values convert units`() {
        val stats = DailyStatsEntity("d", taps = 10, longPresses = 2, swipes = 5, scrolls = 3, distanceMm = 1_609_344.0, touchMs = 90 * 60_000L)
        assertEquals(17.0, Milestones.value(Metric.TOUCHES, stats, DistanceUnit.MILES), 0.0)
        assertEquals(1.0, Milestones.value(Metric.DISTANCE, stats, DistanceUnit.MILES), 1e-9)
        assertEquals(1.609344, Milestones.value(Metric.DISTANCE, stats, DistanceUnit.KILOMETERS), 1e-9)
        assertEquals(90.0, Milestones.value(Metric.TOUCH_TIME, stats, DistanceUnit.MILES), 0.0)
    }

    @Test
    fun `wording matches the examples`() {
        assertEquals("You've scrolled 1,000 times today.", Milestones.message(Metric.SCROLLS, Period.DAY, 1000.0, DistanceUnit.MILES))
        assertEquals(
            "Your finger has traveled 3 miles on the phone this month.",
            Milestones.message(Metric.DISTANCE, Period.MONTH, 3.0, DistanceUnit.MILES),
        )
        assertEquals("1 hour of touching in a day", Milestones.title(Metric.TOUCH_TIME, Period.DAY, 60.0, DistanceUnit.MILES))
        assertEquals("2 min", Milestones.formatProgress(Metric.TOUCH_TIME, 1.7, DistanceUnit.MILES))
        assertEquals("0.01 km", Milestones.formatProgress(Metric.DISTANCE, 0.0061, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `due fires once per period and only when enabled`() {
        val scrolls = MilestoneRuleEntity(1, Metric.SCROLLS.code, Period.DAY.code, 100.0, "mi", enabled = true)
        val off = scrolls.copy(id = 2, enabled = false)
        val totals = mapOf(Period.DAY to DailyStatsEntity("d", scrolls = 120))
        assertEquals(listOf(1L), MilestoneEvaluator.due(listOf(scrolls, off), totals, emptySet(), today, us).map { it.rule.id })
        assertEquals(emptyList<Reached>(), MilestoneEvaluator.due(listOf(scrolls), totals, setOf(1L to "D:2026-10-04"), today, us))
        // Next day, same rule fires again.
        assertEquals(1, MilestoneEvaluator.due(listOf(scrolls), totals, setOf(1L to "D:2026-10-04"), today.plusDays(1), us).size)
        assertEquals(emptyList<Reached>(), MilestoneEvaluator.due(listOf(scrolls), mapOf(Period.DAY to DailyStatsEntity("d", scrolls = 99)), emptySet(), today, us))
    }
}
