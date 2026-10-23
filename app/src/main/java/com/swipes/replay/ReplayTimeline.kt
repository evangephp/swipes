package com.swipes.replay

/**
 * Maps playback time to wall time. In condensed mode, bursts of activity separated by more than
 * [gapMs] are laid end to end with [condensedGapMs] between them, so idle time is skipped.
 */
class ReplayTimeline private constructor(
    private val rangeStartMs: Long,
    private val rangeEndMs: Long,
    /** Activity segments as wall [start, end], sorted and non-overlapping; empty in real-time mode. */
    private val segStart: LongArray,
    private val segEnd: LongArray,
    /** Playback time at which each segment starts. */
    private val segPlay: LongArray,
    private val condensedGapMs: Long,
) {
    val condensed get() = segStart.isNotEmpty()

    val totalMs: Long = if (!condensed) rangeEndMs - rangeStartMs else segPlay.last() + (segEnd.last() - segStart.last())

    fun wallAt(playMs: Long): Long {
        if (!condensed) return rangeStartMs + playMs.coerceIn(0, totalMs)
        val i = lastAtOrBefore(segPlay, playMs).coerceAtLeast(0)
        val offset = playMs - segPlay[i]
        val length = segEnd[i] - segStart[i]
        // Inside the pause between segments, hold at the end of the previous one.
        return segStart[i] + offset.coerceIn(0, length)
    }

    fun playAt(wallMs: Long): Long {
        if (!condensed) return (wallMs - rangeStartMs).coerceIn(0, totalMs)
        val i = lastAtOrBefore(segStart, wallMs)
        if (i < 0) return 0
        val offset = wallMs - segStart[i]
        if (offset <= segEnd[i] - segStart[i]) return segPlay[i] + offset
        // Idle wall time after segment i maps to the end of the following pause.
        return if (i + 1 < segPlay.size) segPlay[i + 1] else totalMs
    }

    private fun lastAtOrBefore(a: LongArray, v: Long): Int {
        var lo = 0
        var hi = a.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] <= v) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** Builds segments incrementally from strokes in start order. */
    class Builder(private val gapMs: Long = 2_000) {
        private val starts = ArrayList<Long>()
        private val ends = ArrayList<Long>()

        fun add(startMs: Long, endMs: Long) {
            if (starts.isNotEmpty() && startMs - ends.last() <= gapMs) {
                ends[ends.size - 1] = maxOf(ends.last(), endMs)
            } else {
                starts += startMs
                ends += endMs
            }
        }

        fun build(rangeStartMs: Long, rangeEndMs: Long, condensed: Boolean, condensedGapMs: Long = 300): ReplayTimeline {
            if (!condensed || starts.isEmpty()) {
                return ReplayTimeline(rangeStartMs, rangeEndMs, LongArray(0), LongArray(0), LongArray(0), condensedGapMs)
            }
            val play = LongArray(starts.size)
            for (i in 1 until starts.size) play[i] = play[i - 1] + (ends[i - 1] - starts[i - 1]) + condensedGapMs
            return ReplayTimeline(rangeStartMs, rangeEndMs, starts.toLongArray(), ends.toLongArray(), play, condensedGapMs)
        }
    }
}
