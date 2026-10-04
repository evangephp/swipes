package com.swipecounter.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    @Test
    fun `at returns value in effect`() {
        val t = Timeline("unknown")
        t.set(10, "a")
        t.set(20, "b")
        assertEquals("unknown", t.at(5))
        assertEquals("a", t.at(10))
        assertEquals("a", t.at(19))
        assertEquals("b", t.at(100))
    }

    @Test
    fun `late arrivals are ordered`() {
        val t = Timeline(0)
        t.set(20, 2)
        t.set(10, 1)
        assertEquals(1, t.at(15))
        assertEquals(2, t.at(25))
    }

    @Test
    fun `during includes value at start and changes inside`() {
        val t = Timeline(0)
        t.set(10, 1)
        t.set(20, 2)
        t.set(30, 3)
        assertEquals(listOf(1, 2), t.during(15, 25))
        assertEquals(listOf(0, 1), t.during(0, 10))
    }

    @Test
    fun `prunes old entries but keeps one before cutoff`() {
        val t = Timeline(0, retainMs = 100)
        t.set(0, 1)
        t.set(50, 2)
        t.set(500, 3)
        assertEquals(2, t.at(450))
        assertEquals(0, t.at(10)) // pruned history reads as the oldest kept value's predecessor: unknown
    }
}
