package com.swipecounter.capture

/**
 * Value history keyed by uptime milliseconds. [at] returns the value in effect at a time, or
 * [unknown] before the first entry. Entries older than [retainMs] behind the newest are pruned.
 */
class Timeline<T>(private val unknown: T, private val retainMs: Long = 10 * 60_000L) {
    private val times = ArrayList<Long>()
    private val values = ArrayList<T>()

    @Synchronized
    fun set(timeMs: Long, value: T) {
        // Late arrivals keep the list sorted; equal times keep the newest value last.
        var i = times.size
        while (i > 0 && times[i - 1] > timeMs) i--
        times.add(i, timeMs)
        values.add(i, value)
        val cutoff = times.last() - retainMs
        // Keep one entry before the cutoff so lookups at the cutoff still resolve.
        var drop = 0
        while (drop + 1 < times.size && times[drop + 1] <= cutoff) drop++
        if (drop > 0) {
            times.subList(0, drop).clear()
            values.subList(0, drop).clear()
        }
    }

    @Synchronized
    fun at(timeMs: Long): T {
        var lo = 0
        var hi = times.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= timeMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (found < 0) unknown else values[found]
    }

    /** Every value in effect at some point during [fromMs]..[toMs]. */
    @Synchronized
    fun during(fromMs: Long, toMs: Long): List<T> {
        val result = mutableListOf(at(fromMs))
        for (i in times.indices) {
            if (times[i] in (fromMs + 1)..toMs) result += values[i]
        }
        return result
    }
}
