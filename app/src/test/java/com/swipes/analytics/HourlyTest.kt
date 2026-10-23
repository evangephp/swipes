package com.swipes.analytics

import com.swipes.data.DailyStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HourlyTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    private fun at(y: Int, m: Int, d: Int, h: Int) = ZonedDateTime.of(y, m, d, h, 30, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `buckets by local hour across a DST change`() {
        // 2026-11-01 01:30 happens twice in Los Angeles; both land in hour 1.
        val firstOneThirty = at(2026, 11, 1, 1)
        val secondOneThirty = firstOneThirty + 3_600_000L
        val hours = Analytics.hourly(
            listOf(
                firstOneThirty to DailyStatsEntity("", taps = 1),
                secondOneThirty to DailyStatsEntity("", taps = 2),
                at(2026, 11, 1, 14) to DailyStatsEntity("", swipes = 5),
            ),
            zone,
        )
        assertEquals(3, hours[1].taps)
        assertEquals(5, hours[14].swipes)
        assertEquals(24, hours.size)
    }

    @Test
    fun `day count includes partial today`() {
        val now = at(2026, 10, 23, 15)
        assertEquals(1, TimeRanges.dayCount(TimeRanges.of(RangePreset.TODAY, now, zone), zone))
        assertEquals(7, TimeRanges.dayCount(TimeRanges.of(RangePreset.LAST_7D, now, zone), zone))
        assertEquals(1, TimeRanges.dayCount(TimeRanges.of(RangePreset.YESTERDAY, now, zone), zone))
    }
}
