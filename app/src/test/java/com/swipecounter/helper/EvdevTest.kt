package com.swipecounter.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class EvdevTest {
    private data class Sample(val slot: Int, val tid: Int, val x: Int, val y: Int)

    private val out = mutableListOf<Sample>()
    private val parser = MtParser(10) { _, slot, tid, x, y, _ -> out += Sample(slot, tid, x, y) }

    private fun abs(code: Int, value: Int) = parser.onEvent(0, Evdev.EV_ABS, code, value)
    private fun syn() = parser.onEvent(0, Evdev.EV_SYN, Evdev.SYN_REPORT, 0)

    @Test
    fun `single finger down move up`() {
        abs(Evdev.ABS_MT_TRACKING_ID, 7); abs(Evdev.ABS_MT_POSITION_X, 100); abs(Evdev.ABS_MT_POSITION_Y, 200); syn()
        abs(Evdev.ABS_MT_POSITION_X, 110); syn()
        abs(Evdev.ABS_MT_TRACKING_ID, -1); syn()
        assertEquals(listOf(Sample(0, 7, 100, 200), Sample(0, 7, 110, 200), Sample(0, -1, 110, 200)), out)
    }

    @Test
    fun `two slots emit independently`() {
        abs(Evdev.ABS_MT_TRACKING_ID, 1); abs(Evdev.ABS_MT_POSITION_X, 10); abs(Evdev.ABS_MT_POSITION_Y, 10); syn()
        abs(Evdev.ABS_MT_SLOT, 1); abs(Evdev.ABS_MT_TRACKING_ID, 2); abs(Evdev.ABS_MT_POSITION_X, 50); abs(Evdev.ABS_MT_POSITION_Y, 60); syn()
        abs(Evdev.ABS_MT_SLOT, 0); abs(Evdev.ABS_MT_POSITION_Y, 12); syn()
        assertEquals(listOf(Sample(0, 1, 10, 10), Sample(1, 2, 50, 60), Sample(0, 1, 10, 12)), out)
    }

    @Test
    fun `syn dropped ends contacts and ignores stale slots`() {
        abs(Evdev.ABS_MT_TRACKING_ID, 1); abs(Evdev.ABS_MT_POSITION_X, 10); abs(Evdev.ABS_MT_POSITION_Y, 10); syn()
        parser.onEvent(0, Evdev.EV_SYN, Evdev.SYN_DROPPED, 0)
        abs(Evdev.ABS_MT_POSITION_X, 99) // lost-state event, ignored
        syn()
        abs(Evdev.ABS_MT_POSITION_X, 20); syn() // no tracking id yet: still ignored
        abs(Evdev.ABS_MT_TRACKING_ID, 3); abs(Evdev.ABS_MT_POSITION_X, 30); syn()
        assertEquals(listOf(Sample(0, 1, 10, 10), Sample(0, -1, 10, 10), Sample(0, 3, 30, 10)), out)
    }

    @Test
    fun `lift on inactive slot is not emitted`() {
        abs(Evdev.ABS_MT_TRACKING_ID, -1); syn()
        assertEquals(emptyList<Sample>(), out)
    }

    @Test
    fun `parses getevent output and prefers touchscreen`() {
        val text = """
            add device 4: /dev/input/event9
              name:     "sec_touchpad"
              events:
                ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_X     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
            add device 5: /dev/input/event8
              name:     "sec_touchscreen"
              events:
                ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_X     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_Y     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
            add device 6: /dev/input/event7
              name:     "sec_vibrator_inputff"
        """.trimIndent()
        val devices = Evdev.parseDevices(text)
        assertEquals(2, devices.size)
        assertEquals(TouchDevice("/dev/input/event8", "sec_touchscreen", 4095, 4095, 10), devices[1])
    }
}
