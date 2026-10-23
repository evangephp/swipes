package com.swipes.capture

import com.swipes.data.HEAT_DOWN
import com.swipes.data.HEAT_PATH
import com.swipes.data.HeatGrid
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class AggregatesTest {
    private val g = PanelGeometry(4095, 4095, 1440, 3120, 500f)

    private fun recorded(wallMs: Long, firstSeq: Long, groupId: Long, multi: Boolean, mask: Mask = Mask.NONE) = RecordedStroke(
        Stroke(firstSeq, 0, 0, wallMs * 1000, 40_000, intArrayOf(0, 10, 10, 0, 8000, 20, 20, 0), groupId, multi),
        StrokeSummary(Kind.SWIPE, true, 3.5f),
        mask,
        g,
    )

    @Test
    fun `day key follows local zone`() {
        // 2026-03-08T07:30Z is still March 7 in Los Angeles.
        val wall = 1772955000000L
        assertEquals("2026-03-07", Aggregates.dayKey(wall, ZoneId.of("America/Los_Angeles")))
        assertEquals("2026-03-08", Aggregates.dayKey(wall, ZoneId.of("UTC")))
    }

    @Test
    fun `multi-finger gesture counted once`() {
        val zone = ZoneId.of("UTC")
        assertEquals(1, Aggregates.dailyDelta(recorded(0, 5, 5, true), zone).multiFinger)
        assertEquals(0, Aggregates.dailyDelta(recorded(0, 6, 5, true), zone).multiFinger)
        val d = Aggregates.dailyDelta(recorded(0, 5, 5, false), zone)
        assertEquals(1, d.swipes)
        assertEquals(1, d.scrolls)
        assertEquals(40L, d.touchMs)
    }

    @Test
    fun `keyboard strokes count only as typing`() {
        val d = Aggregates.dailyDelta(recorded(0, 5, 5, true, Mask.IME), ZoneId.of("UTC"))
        assertEquals(listOf(0, 0, 0, 0, 1), listOf(d.taps, d.swipes, d.scrolls, d.multiFinger, d.typing))
        assertEquals(40L, d.touchMs)
        assertEquals(1, d.touches)
        // Lock-screen strokes are still real taps/swipes.
        assertEquals(1, Aggregates.dailyDelta(recorded(0, 5, 5, false, Mask.LOCK), ZoneId.of("UTC")).swipes)
    }

    @Test
    fun `heat skips masked strokes and buckets by utc hour`() {
        val grids = HashMap<Pair<Long, Int>, HeatGrid>()
        Aggregates.addHeat(recorded(3_600_000L * 5 + 1234, 0, 0, false), grids)
        Aggregates.addHeat(recorded(3_600_000L * 5, 1, 1, false, Mask.IME), grids)
        assertEquals(setOf(3_600_000L * 5 to HEAT_DOWN, 3_600_000L * 5 to HEAT_PATH), grids.keys)
        assertEquals(1, grids.getValue(3_600_000L * 5 to HEAT_DOWN).counts.sum())
        assertEquals(2, grids.getValue(3_600_000L * 5 to HEAT_PATH).counts.sum())
    }
}
