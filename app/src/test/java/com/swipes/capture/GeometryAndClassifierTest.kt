package com.swipes.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryAndClassifierTest {
    // S25 Ultra-like: 4096x4096 panel onto 1440x3120 at ~500 dpi.
    private val g = PanelGeometry(4095, 4095, 1440, 3120, 500f)

    @Test
    fun `rotation maps natural corners like InputReader`() {
        // Top-left of the natural panel.
        assertEquals(0f, g.screenX(0, 0, 0), 0.01f)
        assertEquals(0f, g.screenX(0, 0, 1), 0.01f)
        assertEquals(1440f, g.screenY(0, 0, 1), 0.01f)
        assertEquals(1440f, g.screenX(0, 0, 2), 0.01f)
        assertEquals(3120f, g.screenY(0, 0, 2), 0.01f)
        assertEquals(3120f, g.screenX(0, 0, 3), 0.01f)
        assertEquals(0f, g.screenY(0, 0, 3), 0.01f)
    }

    @Test
    fun `mm uses physical pixels`() {
        // 500 px at 500 dpi is one inch.
        assertEquals(25.4f, g.mm(500 * 4096 / 1440f, 0f), 0.05f)
    }

    private fun stroke(vararg xy: Int, durationMs: Long = 50): Stroke {
        val n = xy.size / 2
        val points = IntArray(n * POINT_STRIDE)
        for (i in 0 until n) {
            points[i * POINT_STRIDE] = if (i == 0) 0 else 8000
            points[i * POINT_STRIDE + 1] = xy[2 * i]
            points[i * POINT_STRIDE + 2] = xy[2 * i + 1]
        }
        return Stroke(0, 0, 0, 0, durationMs * 1000, points, 0, false)
    }

    @Test
    fun `classifies tap long press swipe and scroll`() {
        assertEquals(Kind.TAP, Classifier.summarize(stroke(1000, 1000, 1004, 1002), g, 0).kind)
        assertEquals(Kind.LONG_PRESS, Classifier.summarize(stroke(1000, 1000, 1004, 1002, durationMs = 800), g, 0).kind)
        val vertical = Classifier.summarize(stroke(2000, 3000, 2010, 1000), g, 0)
        assertEquals(Kind.SWIPE, vertical.kind)
        assertTrue(vertical.scroll)
        assertFalse(Classifier.summarize(stroke(500, 2000, 3500, 2050), g, 0).scroll)
    }

    @Test
    fun `scroll direction follows screen rotation`() {
        // Moving along panel x is vertical on screen in landscape.
        assertTrue(Classifier.summarize(stroke(500, 2000, 3500, 2050), g, 1).scroll)
    }
}
