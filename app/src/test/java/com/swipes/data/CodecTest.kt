package com.swipes.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecTest {
    @Test
    fun `points round trip including negative deltas`() {
        val points = intArrayOf(0, 4000, 100, 30, 8333, 3990, 180, 28, 8334, 4095, 0, 255, 1_000_000, 0, 4095, 0)
        val bytes = PointCodec.encode(points)
        assertArrayEquals(points, PointCodec.decode(bytes, 4))
    }

    @Test
    fun `typical swipe is compact`() {
        val n = 120
        val points = IntArray(n * 4) { i -> when (i % 4) { 0 -> 8333; 1 -> 2000 + i; 2 -> 3000 - 2 * i; else -> 40 } }
        assertEquals(true, PointCodec.encode(points).size <= n * 6)
    }

    @Test
    fun `heat grid round trips and clamps`() {
        val grid = HeatGrid()
        grid.add(0, 0, 4095, 4095)
        grid.add(4095, 4095, 4095, 4095)
        grid.add(9999, -5, 4095, 4095)
        val decoded = HeatGrid.decode(grid.encode())
        assertArrayEquals(grid.counts, decoded.counts)
        assertEquals(1, decoded.counts[0])
        assertEquals(1, decoded.counts[HeatGrid.WIDTH * HeatGrid.HEIGHT - 1])
        assertEquals(1, decoded.counts[HeatGrid.WIDTH - 1])
    }
}
