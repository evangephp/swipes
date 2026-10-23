package com.swipes.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeRangeTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val now = ZonedDateTime.of(2026, 3, 9, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
    private fun ms(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `presets align to local midnights`() {
        assertEquals(TimeRange(ms(2026, 3, 9), now), TimeRanges.of(RangePreset.TODAY, now, zone))
        assertEquals(TimeRange(ms(2026, 3, 8), ms(2026, 3, 9)), TimeRanges.of(RangePreset.YESTERDAY, now, zone))
        assertEquals(TimeRange(ms(2026, 3, 3), now), TimeRanges.of(RangePreset.LAST_7D, now, zone))
        assertEquals(TimeRange(ms(2026, 3, 1), now), TimeRanges.of(RangePreset.MONTH, now, zone))
        assertEquals(TimeRange(ms(2026, 1, 1), now), TimeRanges.of(RangePreset.YTD, now, zone))
    }

    @Test
    fun `yesterday across spring-forward is 23 hours`() {
        // DST began 2026-03-08 in Los Angeles.
        assertEquals(23 * 3_600_000L, TimeRanges.of(RangePreset.YESTERDAY, now, zone).durationMs)
    }

    @Test
    fun `custom range is inclusive and capped at now`() {
        val r = TimeRanges.of(RangePreset.CUSTOM, now, zone, LocalDate.of(2026, 3, 1) to LocalDate.of(2026, 3, 9))
        assertEquals(TimeRange(ms(2026, 3, 1), now), r)
        assertEquals("2026-03-01" to "2026-03-09", TimeRanges.dayKeys(r, zone))
    }

    @Test
    fun `day alignment decides the aggregate path`() {
        assertTrue(TimeRanges.isDayAligned(TimeRanges.of(RangePreset.MONTH, now, zone), now, zone))
        assertTrue(TimeRanges.isDayAligned(TimeRanges.of(RangePreset.YESTERDAY, now, zone), now, zone))
        assertFalse(TimeRanges.isDayAligned(TimeRanges.of(RangePreset.LAST_2H, now, zone), now, zone))
    }
}
