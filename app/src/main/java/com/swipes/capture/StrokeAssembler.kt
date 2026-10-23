package com.swipes.capture

/** Ints per point in [Stroke.points]: [dtUs since previous point, x, y, touchMajor]. */
const val POINT_STRIDE = 4

/** One contact from finger-down to finger-up, in panel units. */
class Stroke(
    /** Helper seq of the first sample; with the helper instance this identifies the stroke. */
    val firstSeq: Long,
    val slot: Int,
    val startMonoUs: Long,
    val startWallUs: Long,
    val endMonoUs: Long,
    val points: IntArray,
    /** firstSeq of the first stroke of the touch group (fingers overlapping in time). */
    val groupId: Long,
    /** Another finger was down at some point during this stroke. */
    val multiFinger: Boolean,
) {
    val pointCount get() = points.size / POINT_STRIDE
    val durationMs get() = (endMonoUs - startMonoUs) / 1000

    fun x(i: Int) = points[i * POINT_STRIDE + 1]

    fun y(i: Int) = points[i * POINT_STRIDE + 2]

    fun major(i: Int) = points[i * POINT_STRIDE + 3]

    /** Copy with coordinates removed, for masked strokes kept in memory for display. */
    fun withoutPoints() = Stroke(firstSeq, slot, startMonoUs, startWallUs, endMonoUs, IntArray(0), groupId, multiFinger)
}

/** Turns per-slot samples into [Stroke]s, emitted on lift. */
class StrokeAssembler(private val onStroke: (Stroke) -> Unit) {

    private class Open(val firstSeq: Long, val trackingId: Int, val startMono: Long, val startWall: Long, val groupId: Long) {
        var points = IntArray(64 * POINT_STRIDE)
        var size = 0
        var lastMono = startMono
        var multiFinger = false

        fun add(mono: Long, x: Int, y: Int, major: Int) {
            if (size + POINT_STRIDE > points.size) points = points.copyOf(points.size * 2)
            points[size] = (mono - lastMono).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            points[size + 1] = x
            points[size + 2] = y
            points[size + 3] = major
            size += POINT_STRIDE
            lastMono = mono
        }
    }

    private val open = HashMap<Int, Open>()
    private var groupId = -1L

    /** Seq of the oldest sample still held in an unfinished stroke, or null. */
    fun oldestOpenSeq(): Long? = open.values.minOfOrNull { it.firstSeq }

    fun add(seq: Long, monoUs: Long, wallUs: Long, slot: Int, trackingId: Int, x: Int, y: Int, major: Int) {
        val current = open[slot]
        if (trackingId == -1) {
            if (current != null) finish(slot, current)
            return
        }
        if (current != null && current.trackingId != trackingId) finish(slot, current)
        val stroke = open[slot] ?: start(seq, slot, trackingId, monoUs, wallUs)
        stroke.add(monoUs, x, y, major)
        if (stroke.size >= MAX_POINTS * POINT_STRIDE) {
            // Split pathological holds so a single row stays bounded.
            finish(slot, stroke)
        }
    }

    /** Ends all open strokes as they are, e.g. after the sample stream was interrupted. */
    fun flushOpen() {
        for ((slot, stroke) in open.toList()) finish(slot, stroke)
    }

    private fun start(seq: Long, slot: Int, trackingId: Int, monoUs: Long, wallUs: Long): Open {
        if (open.isEmpty()) groupId = seq
        val stroke = Open(seq, trackingId, monoUs, wallUs, groupId)
        open[slot] = stroke
        if (open.size > 1) open.values.forEach { it.multiFinger = true }
        return stroke
    }

    private fun finish(slot: Int, s: Open) {
        open.remove(slot)
        if (s.size == 0) return
        onStroke(Stroke(s.firstSeq, slot, s.startMono, s.startWall, s.lastMono, s.points.copyOf(s.size), s.groupId, s.multiFinger))
    }

    companion object {
        const val MAX_POINTS = 60_000
    }
}
