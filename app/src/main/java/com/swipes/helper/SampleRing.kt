package com.swipes.helper

import com.swipes.helper.Protocol.STRIDE

/** Fixed-capacity buffer of packed samples addressed by a monotonically increasing sequence number. */
class SampleRing(private val capacity: Int) {
    private val data = LongArray(capacity * STRIDE)
    private var nextSeq = 0L

    @Synchronized fun nextSeq() = nextSeq

    @Synchronized fun oldestSeq() = maxOf(0L, nextSeq - capacity)

    @Synchronized
    fun add(monoUs: Long, wallUs: Long, slot: Int, trackingId: Int, x: Int, y: Int, major: Int) {
        val o = (nextSeq % capacity).toInt() * STRIDE
        data[o + Protocol.F_MONO] = monoUs
        data[o + Protocol.F_WALL] = wallUs
        data[o + Protocol.F_SLOT] = slot.toLong()
        data[o + Protocol.F_TRACKING_ID] = trackingId.toLong()
        data[o + Protocol.F_X] = x.toLong()
        data[o + Protocol.F_Y] = y.toLong()
        data[o + Protocol.F_MAJOR] = major.toLong()
        nextSeq++
    }

    /** Wakes threads blocked in [awaitAfter]; call once per batch of [add]s. */
    @Synchronized fun publish() = (this as Object).notifyAll()

    /** Blocks until a sample with seq >= [seq] exists or [timeoutMs] passes. */
    @Synchronized
    fun awaitAfter(seq: Long, timeoutMs: Long) {
        if (nextSeq <= seq) (this as Object).wait(timeoutMs)
    }

    /** Copies up to [max] samples starting at [fromSeq] (clamped to the oldest retained). */
    @Synchronized
    fun copy(fromSeq: Long, max: Int): Pair<Long, LongArray> {
        val first = fromSeq.coerceIn(oldestSeq(), nextSeq)
        val count = minOf(max.toLong(), nextSeq - first).toInt()
        val out = LongArray(count * STRIDE)
        for (i in 0 until count) {
            val o = ((first + i) % capacity).toInt() * STRIDE
            System.arraycopy(data, o, out, i * STRIDE, STRIDE)
        }
        return first to out
    }
}
