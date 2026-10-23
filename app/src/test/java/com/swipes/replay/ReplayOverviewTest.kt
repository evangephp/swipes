package com.swipes.replay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayOverviewTest {
    // Real-time timeline over 0..1000 ms, 10 bins of 100 ms.
    private val timeline = ReplayTimeline.Builder().build(0, 1000, condensed = false)

    private fun overview(topApps: Int = 6) = ReplayOverview.Builder(timeline, 10) { it == "systemui" }.apply {
        add(50, "reddit")
        add(150, "reddit")
        add(160, "wechat")
        add(170, "wechat")
        add(550, "systemui")
        add(950, null)
        add(960, "chrome")
    }.build(topApps)

    @Test
    fun `volume per bin`() {
        assertArrayEquals(intArrayOf(1, 3, 0, 0, 0, 1, 0, 0, 0, 2), overview().volume)
    }

    @Test
    fun `categories rank apps then system and other`() {
        val o = overview(topApps = 2)
        assertEquals(
            listOf(TallyCategory.App("reddit"), TallyCategory.App("wechat"), TallyCategory.System, TallyCategory.Other),
            o.categories,
        )
        // chrome (ranked out) and the unknown package both fold into Other.
        assertArrayEquals(intArrayOf(2, 2, 1, 2), o.tallyAt(1000))
    }

    @Test
    fun `tally interpolates within a bin`() {
        val o = overview()
        assertEquals(0, o.tallyAt(0)[0])
        assertEquals(1, o.tallyAt(100)[0]) // end of bin 0
        assertEquals(1, o.tallyAt(150)[0]) // halfway through bin 1 (holds 1 more): 1.5 → 1
        assertEquals(2, o.tallyAt(200)[0])
    }
}
