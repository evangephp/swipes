package com.swipecounter.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    private val g = PanelGeometry(4095, 4095, 1440, 3120, 500f)

    /** Helper stroke moving straight down screen x=720 from y=1000 to y=2000 over 100 ms at 120 Hz. */
    private val helper: Stroke = run {
        val n = 13
        val points = IntArray(n * POINT_STRIDE)
        for (i in 0 until n) {
            points[i * POINT_STRIDE] = if (i == 0) 0 else 8333
            points[i * POINT_STRIDE + 1] = (720 * 4096 / 1440f).toInt()
            points[i * POINT_STRIDE + 2] = ((1000 + 1000 * i / (n - 1f)) * 4096 / 3120f).toInt()
        }
        Stroke(0, 0, 5_000_000, 0, 5_100_000, points, 0, false)
    }

    @Test
    fun `matching trace has small error and correct rate`() {
        val app = (0..10).map { ObservedPoint(5000L + it * 10, 720f, 1000f + 100f * it) }
        val r = Validation.compare(app, listOf(helper), g, 0)
        assertNotNull(r)
        r!!
        assertEquals(0f, r.meanErrorPx, 3f)
        assertEquals(120.0, r.helperHz, 1.0)
    }

    @Test
    fun `offset trace reports the offset`() {
        val app = (0..10).map { ObservedPoint(5000L + it * 10, 770f, 1000f + 100f * it) }
        assertEquals(50f, Validation.compare(app, listOf(helper), g, 0)!!.meanErrorPx, 3f)
    }

    @Test
    fun `no helper stroke near the app's touch-down`() {
        assertNull(Validation.compare(listOf(ObservedPoint(9000, 0f, 0f)), listOf(helper), g, 0))
    }
}
