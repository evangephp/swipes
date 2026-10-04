package com.swipecounter.capture

import kotlin.math.abs
import kotlin.math.hypot

/** A point the app itself observed on its draw pad: uptime ms and screen px. */
data class ObservedPoint(val timeMs: Long, val x: Float, val y: Float)

data class ValidationResult(
    val meanErrorPx: Float,
    val maxErrorPx: Float,
    val helperHz: Double,
    val helperPoints: Int,
    val appPoints: Int,
)

/** Checks the helper's view of a stroke against what the app saw for the same finger. */
object Validation {
    /** Helper and app timestamps for the same touch-down should agree well within this. */
    const val MAX_START_SKEW_MS = 150L

    fun compare(app: List<ObservedPoint>, candidates: List<Stroke>, geometry: PanelGeometry, rotation: Int): ValidationResult? {
        if (app.isEmpty()) return null
        val start = app.first().timeMs
        val stroke = candidates
            .filter { it.pointCount > 1 }
            .minByOrNull { abs(it.startMonoUs / 1000 - start) }
            ?.takeIf { abs(it.startMonoUs / 1000 - start) <= MAX_START_SKEW_MS }
            ?: return null

        val n = stroke.pointCount
        val times = LongArray(n)
        var t = stroke.startMonoUs
        for (i in 0 until n) {
            t += stroke.points[i * POINT_STRIDE]
            times[i] = t
        }
        val xs = FloatArray(n) { geometry.screenX(stroke.x(it), stroke.y(it), rotation) }
        val ys = FloatArray(n) { geometry.screenY(stroke.x(it), stroke.y(it), rotation) }

        var sum = 0f
        var max = 0f
        var j = 0
        for (p in app) {
            val us = p.timeMs * 1000
            while (j < n - 2 && times[j + 1] < us) j++
            // Linear interpolation between neighbouring helper samples, clamped at the ends.
            val f = ((us - times[j]).toFloat() / (times[j + 1] - times[j]).coerceAtLeast(1)).coerceIn(0f, 1f)
            val hx = xs[j] + (xs[j + 1] - xs[j]) * f
            val hy = ys[j] + (ys[j + 1] - ys[j]) * f
            val err = hypot(hx - p.x, hy - p.y)
            sum += err
            max = maxOf(max, err)
        }
        val span = times[n - 1] - times[0]
        return ValidationResult(sum / app.size, max, if (span > 0) (n - 1) * 1e6 / span else 0.0, n, app.size)
    }
}
