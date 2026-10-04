package com.swipecounter.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyMaskTest {
    private val g = PanelGeometry(4095, 4095, 1440, 3120, 500f)
    private val keyboard = Bounds(0, 2000, 1440, 3120)

    // Panel y 3500 maps to screen y ~2666 (inside keyboard); 1000 maps to ~762.
    private fun stroke(startMs: Long, endMs: Long, vararg panelXY: Int): Stroke {
        val n = panelXY.size / 2
        val points = IntArray(n * POINT_STRIDE)
        for (i in 0 until n) {
            points[i * POINT_STRIDE + 1] = panelXY[2 * i]
            points[i * POINT_STRIDE + 2] = panelXY[2 * i + 1]
        }
        return Stroke(0, 0, startMs * 1000, 0, endMs * 1000, points, 0, false)
    }

    private fun context(locked: Boolean? = false, ime: ImeState = ImeState.Hidden) = ScreenContext().apply {
        this.locked.set(0, locked)
        this.ime.set(0, ime)
    }

    @Test
    fun `unknown lock state masks`() {
        val ctx = ScreenContext().apply { ime.set(0, ImeState.Hidden) }
        assertEquals(Mask.LOCK, PrivacyMask.decide(stroke(100, 150, 2000, 1000), g, ctx))
    }

    @Test
    fun `stroke starting while locked masks even if unlocked before it ends`() {
        val ctx = context(locked = true).apply { locked.set(120, false) }
        assertEquals(Mask.LOCK, PrivacyMask.decide(stroke(100, 150, 2000, 1000), g, ctx))
        assertEquals(Mask.NONE, PrivacyMask.decide(stroke(130, 150, 2000, 1000), g, ctx))
    }

    @Test
    fun `keyboard masks only strokes touching it`() {
        val ctx = context(ime = ImeState.Visible(keyboard))
        assertEquals(Mask.IME, PrivacyMask.decide(stroke(100, 150, 2000, 3500), g, ctx))
        assertEquals(Mask.NONE, PrivacyMask.decide(stroke(100, 150, 2000, 1000), g, ctx))
        // Scroll that starts above the keyboard and crosses into it.
        assertEquals(Mask.IME, PrivacyMask.decide(stroke(100, 150, 2000, 1000, 2000, 3500), g, ctx))
    }

    @Test
    fun `keyboard appearing during the stroke counts`() {
        val ctx = context().apply { ime.set(120, ImeState.Visible(keyboard)) }
        assertEquals(Mask.IME, PrivacyMask.decide(stroke(100, 150, 2000, 3500), g, ctx))
    }

    @Test
    fun `unknown keyboard state masks`() {
        assertEquals(Mask.IME, PrivacyMask.decide(stroke(100, 150, 2000, 1000), g, context(ime = ImeState.Unknown)))
    }
}
