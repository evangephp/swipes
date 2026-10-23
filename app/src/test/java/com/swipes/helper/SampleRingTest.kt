package com.swipes.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleRingTest {
    private fun SampleRing.addX(x: Int) = add(x.toLong(), 0, 0, 1, x, 0, 0)

    @Test
    fun `copy returns samples in order`() {
        val ring = SampleRing(4)
        (0 until 3).forEach { ring.addX(it) }
        val (first, data) = ring.copy(1, 10)
        assertEquals(1L, first)
        assertEquals(listOf(1L, 2L), (0 until data.size / Protocol.STRIDE).map { data[it * Protocol.STRIDE + Protocol.F_X] })
    }

    @Test
    fun `overrun clamps to oldest retained`() {
        val ring = SampleRing(4)
        (0 until 10).forEach { ring.addX(it) }
        assertEquals(6L, ring.oldestSeq())
        val (first, data) = ring.copy(0, 10)
        assertEquals(6L, first)
        assertEquals(listOf(6L, 7L, 8L, 9L), (0 until 4).map { data[it * Protocol.STRIDE + Protocol.F_X] })
    }

    @Test
    fun `copy respects max`() {
        val ring = SampleRing(8)
        (0 until 5).forEach { ring.addX(it) }
        assertEquals(2, ring.copy(0, 2).second.size / Protocol.STRIDE)
    }
}
