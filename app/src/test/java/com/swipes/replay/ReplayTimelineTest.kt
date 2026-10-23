package com.swipes.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReplayTimelineTest {
    private fun builder() = ReplayTimeline.Builder(gapMs = 2_000).apply {
        add(10_000, 10_500)
        add(11_000, 11_800) // within 2 s: same segment 10.0–11.8
        add(60_000, 60_400) // new segment
    }

    @Test
    fun `condensed skips idle gaps`() {
        val t = builder().build(0, 100_000, condensed = true, condensedGapMs = 300)
        assertEquals(1_800 + 300 + 400L, t.totalMs)
        assertEquals(10_000L, t.wallAt(0))
        assertEquals(11_000L, t.wallAt(1_000))
        assertEquals(11_800L, t.wallAt(1_900)) // in the pause: hold at segment end
        assertEquals(60_000L, t.wallAt(2_100))
        assertEquals(60_400L, t.wallAt(99_999))
    }

    @Test
    fun `playAt inverts wallAt inside segments`() {
        val t = builder().build(0, 100_000, condensed = true, condensedGapMs = 300)
        for (play in listOf(0L, 700L, 1_800L, 2_100L, 2_350L)) assertEquals(play, t.playAt(t.wallAt(play)))
        assertEquals(1_800L + 300, t.playAt(30_000)) // idle wall time maps to the end of the pause
    }

    @Test
    fun `real time maps linearly over the range`() {
        val t = builder().build(5_000, 100_000, condensed = false)
        assertFalse(t.condensed)
        assertEquals(95_000L, t.totalMs)
        assertEquals(15_000L, t.wallAt(10_000))
        assertEquals(10_000L, t.playAt(15_000))
    }
}
