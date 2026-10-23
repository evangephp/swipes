package com.swipes.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeAssemblerTest {
    private val strokes = mutableListOf<Stroke>()
    private val assembler = StrokeAssembler { strokes += it }
    private var seq = 0L

    private fun sample(t: Long, slot: Int, tid: Int, x: Int = 0, y: Int = 0) =
        assembler.add(seq++, t * 1000, t * 1000 + 5_000_000, slot, tid, x, y, 3)

    @Test
    fun `stroke emitted on lift with deltas`() {
        sample(0, 0, 1, 10, 20)
        sample(8, 0, 1, 15, 25)
        sample(16, 0, -1)
        val s = strokes.single()
        assertEquals(2, s.pointCount)
        assertEquals(8000, s.points[POINT_STRIDE]) // dt of second point, us
        assertEquals(15, s.x(1))
        assertEquals(8L, s.durationMs)
        assertEquals(0L, s.firstSeq)
        assertFalse(s.multiFinger)
    }

    @Test
    fun `overlapping fingers share a group and are multi-finger`() {
        sample(0, 0, 1)
        sample(5, 1, 2)
        sample(10, 0, -1)
        sample(15, 1, -1)
        sample(20, 0, 3) // new group after all lifted
        sample(25, 0, -1)
        assertEquals(3, strokes.size)
        assertEquals(strokes[0].groupId, strokes[1].groupId)
        assertTrue(strokes[0].multiFinger && strokes[1].multiFinger)
        assertEquals(4L, strokes[2].groupId)
        assertFalse(strokes[2].multiFinger)
    }

    @Test
    fun `tracking id change without lift ends previous stroke`() {
        sample(0, 0, 1)
        sample(5, 0, 2)
        sample(10, 0, -1)
        assertEquals(2, strokes.size)
    }

    @Test
    fun `oldest open seq tracks unfinished strokes`() {
        sample(0, 0, 1)
        sample(1, 1, 2)
        assertEquals(0L, assembler.oldestOpenSeq())
        sample(2, 0, -1)
        assertEquals(1L, assembler.oldestOpenSeq())
        assembler.flushOpen()
        assertEquals(null, assembler.oldestOpenSeq())
        assertEquals(2, strokes.size)
    }
}
