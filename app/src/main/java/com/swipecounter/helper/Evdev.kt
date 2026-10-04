package com.swipecounter.helper

data class TouchDevice(val path: String, val name: String, val xMax: Int, val yMax: Int, val slots: Int)

object Evdev {
    const val EVENT_SIZE = 24 // struct input_event on 64-bit
    const val EV_SYN = 0x00
    const val EV_ABS = 0x03
    const val SYN_REPORT = 0x00
    const val SYN_DROPPED = 0x03
    const val ABS_MT_SLOT = 0x2f
    const val ABS_MT_TOUCH_MAJOR = 0x30
    const val ABS_MT_POSITION_X = 0x35
    const val ABS_MT_POSITION_Y = 0x36
    const val ABS_MT_TRACKING_ID = 0x39

    private val DEVICE_LINE = Regex("""^add device \d+: (\S+)""")
    private val NAME_LINE = Regex("""^\s*name:\s*"(.*)"""")
    private val ABS_LINE = Regex("""(ABS_MT_\w+)\s*:\s*value -?\d+, min -?\d+, max (-?\d+)""")

    /** Picks the multitouch device from `getevent -pl`, preferring one named "touchscreen". */
    fun findTouchscreen(): TouchDevice? {
        val out = ProcessBuilder("/system/bin/getevent", "-pl")
            .redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText()
        return parseDevices(out).let { found ->
            found.firstOrNull { "touchscreen" in it.name.lowercase() } ?: found.firstOrNull()
        }
    }

    fun parseDevices(getevent: String): List<TouchDevice> {
        val found = mutableListOf<TouchDevice>()
        var path: String? = null
        var name = ""
        var axes = mutableMapOf<String, Int>()
        fun commit() {
            val p = path ?: return
            val xMax = axes["ABS_MT_POSITION_X"] ?: return
            found += TouchDevice(p, name, xMax, axes["ABS_MT_POSITION_Y"] ?: 0, (axes["ABS_MT_SLOT"] ?: 0) + 1)
        }
        for (line in getevent.lineSequence()) {
            val add = DEVICE_LINE.find(line)
            if (add != null) {
                commit()
                path = add.groupValues[1]
                name = ""
                axes = mutableMapOf()
                continue
            }
            NAME_LINE.find(line)?.let { name = it.groupValues[1] }
            ABS_LINE.find(line)?.let { axes[it.groupValues[1]] = it.groupValues[2].toInt() }
        }
        commit()
        return found
    }
}

/**
 * Multitouch protocol B decoder. Emits one sample per changed active slot per SYN_REPORT,
 * and a lift (trackingId = -1) when a slot's contact ends.
 */
class MtParser(
    slotCount: Int,
    private val emit: (wallUs: Long, slot: Int, trackingId: Int, x: Int, y: Int, major: Int) -> Unit,
) {
    private val trackingId = IntArray(slotCount) { -1 }
    private val x = IntArray(slotCount)
    private val y = IntArray(slotCount)
    private val major = IntArray(slotCount)
    private val dirty = BooleanArray(slotCount)
    private val active = BooleanArray(slotCount)
    private var slot = 0
    private var dropping = false

    fun onEvent(wallUs: Long, type: Int, code: Int, value: Int) {
        if (type == Evdev.EV_SYN) {
            when (code) {
                Evdev.SYN_REPORT -> if (dropping) resync(wallUs) else emitFrame(wallUs)
                Evdev.SYN_DROPPED -> dropping = true
            }
            return
        }
        if (type != Evdev.EV_ABS || dropping) return
        if (code == Evdev.ABS_MT_SLOT) {
            slot = value
            return
        }
        if (slot !in trackingId.indices) return
        when (code) {
            Evdev.ABS_MT_TRACKING_ID -> trackingId[slot] = value
            Evdev.ABS_MT_POSITION_X -> x[slot] = value
            Evdev.ABS_MT_POSITION_Y -> y[slot] = value
            Evdev.ABS_MT_TOUCH_MAJOR -> major[slot] = value
            else -> return
        }
        dirty[slot] = true
    }

    private fun emitFrame(wallUs: Long) {
        for (s in dirty.indices) {
            if (!dirty[s]) continue
            dirty[s] = false
            if (trackingId[s] == -1) {
                if (active[s]) emit(wallUs, s, -1, x[s], y[s], major[s])
                active[s] = false
            } else {
                active[s] = true
                emit(wallUs, s, trackingId[s], x[s], y[s], major[s])
            }
        }
    }

    // Events were lost; we can't re-read slot state without ioctls, so end every contact and
    // ignore positions until each slot reports a fresh tracking id.
    private fun resync(wallUs: Long) {
        dropping = false
        for (s in trackingId.indices) {
            if (active[s]) emit(wallUs, s, -1, x[s], y[s], major[s])
            active[s] = false
            dirty[s] = false
            trackingId[s] = -1
        }
    }
}
