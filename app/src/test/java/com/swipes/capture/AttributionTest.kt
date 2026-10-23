package com.swipes.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttributionTest {
    private val app = WindowSnap(1, Bounds(0, 0, 1440, 3120), 1, "com.reddit.frontpage")
    private val headsUp = WindowSnap(5, Bounds(0, 0, 1440, 400), 3, "com.android.systemui")
    private val keyboard = WindowSnap(6, Bounds(0, 2000, 1440, 3120), 2, "com.samsung.android.honeyboard")
    private val windows = listOf(app, headsUp, keyboard)

    @Test
    fun `heads-up only owns touches inside it`() {
        assertEquals("com.android.systemui", Attribution.packageAt(windows, 700f, 200f))
        assertEquals("com.reddit.frontpage", Attribution.packageAt(windows, 700f, 1200f))
    }

    @Test
    fun `keyboard touches belong to the app being typed in`() {
        assertEquals("com.reddit.frontpage", Attribution.packageAt(windows, 700f, 2500f))
    }

    @Test
    fun `no snapshot or no window gives null`() {
        assertNull(Attribution.packageAt(null, 1f, 1f))
        assertNull(Attribution.packageAt(listOf(headsUp), 700f, 1200f))
    }
}
