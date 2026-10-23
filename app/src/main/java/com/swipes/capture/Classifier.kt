package com.swipes.capture

import kotlin.math.abs

enum class Kind(val code: Int) { TAP(0), LONG_PRESS(1), SWIPE(2) }

data class StrokeSummary(
    val kind: Kind,
    /** Swipe whose dominant on-screen direction is vertical. */
    val scroll: Boolean,
    val pathMm: Float,
)

object Classifier {
    /** Movement under this (mm from the start point) still counts as a tap/press; ~Android touch slop. */
    const val SLOP_MM = 1.5f
    const val LONG_PRESS_MS = 500L

    fun summarize(stroke: Stroke, geometry: PanelGeometry, rotation: Int): StrokeSummary {
        val x0 = stroke.x(0)
        val y0 = stroke.y(0)
        var path = 0f
        var maxDisplacement = 0f
        for (i in 1 until stroke.pointCount) {
            path += geometry.mm((stroke.x(i) - stroke.x(i - 1)).toFloat(), (stroke.y(i) - stroke.y(i - 1)).toFloat())
            maxDisplacement = maxOf(maxDisplacement, geometry.mm((stroke.x(i) - x0).toFloat(), (stroke.y(i) - y0).toFloat()))
        }
        val kind = when {
            maxDisplacement >= SLOP_MM -> Kind.SWIPE
            stroke.durationMs >= LONG_PRESS_MS -> Kind.LONG_PRESS
            else -> Kind.TAP
        }
        val last = stroke.pointCount - 1
        val dx = geometry.screenX(stroke.x(last), stroke.y(last), rotation) - geometry.screenX(x0, y0, rotation)
        val dy = geometry.screenY(stroke.x(last), stroke.y(last), rotation) - geometry.screenY(x0, y0, rotation)
        return StrokeSummary(kind, kind == Kind.SWIPE && abs(dy) > abs(dx), path)
    }
}
