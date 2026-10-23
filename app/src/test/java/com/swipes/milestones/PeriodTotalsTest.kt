package com.swipes.milestones

import com.swipes.data.DailyStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodTotalsTest {
    private val totals = PeriodTotals("2026-10-22", mapOf(Period.DAY to DailyStatsEntity("2026-10-22", scrolls = 1500)))

    @Test
    fun `same day adds pending`() {
        assertEquals(1510, totals.withPending("2026-10-22", DailyStatsEntity("2026-10-22", scrolls = 10))!![Period.DAY]!!.scrolls)
    }

    @Test
    fun `after midnight yesterday's totals are refused`() {
        assertNull(totals.withPending("2026-10-23", DailyStatsEntity("2026-10-23")))
    }
}
