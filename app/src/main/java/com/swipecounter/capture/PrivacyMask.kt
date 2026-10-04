package com.swipecounter.capture

/** Why a stroke's coordinates were dropped before storage. */
enum class Mask(val code: Int) { NONE(0), IME(1), LOCK(2) }

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

sealed interface ImeState {
    data object Unknown : ImeState
    data object Hidden : ImeState
    data class Visible(val bounds: Bounds) : ImeState
}

/** What was on screen over time, fed by the accessibility service and system broadcasts. */
class ScreenContext {
    val ime = Timeline<ImeState>(ImeState.Unknown)
    /** null = unknown, which is treated as locked. */
    val locked = Timeline<Boolean?>(null)
    val rotation = Timeline(0)
    val foregroundPackage = Timeline<String?>(null)
}

/**
 * Privacy-first: a stroke is masked if it started while the device was locked (or lock state is
 * unknown), or if any point fell inside the keyboard while it was visible (or keyboard state is unknown).
 */
object PrivacyMask {
    fun decide(stroke: Stroke, geometry: PanelGeometry, context: ScreenContext): Mask {
        val startMs = stroke.startMonoUs / 1000
        val endMs = stroke.endMonoUs / 1000
        if (context.locked.at(startMs) != false) return Mask.LOCK

        val imeStates = context.ime.during(startMs, endMs)
        if (imeStates.any { it is ImeState.Unknown }) return Mask.IME
        val keyboards = imeStates.filterIsInstance<ImeState.Visible>().map { it.bounds }
        if (keyboards.isEmpty()) return Mask.NONE
        val rotation = context.rotation.at(startMs)
        for (i in 0 until stroke.pointCount) {
            val x = geometry.screenX(stroke.x(i), stroke.y(i), rotation)
            val y = geometry.screenY(stroke.x(i), stroke.y(i), rotation)
            if (keyboards.any { it.contains(x, y) }) return Mask.IME
        }
        return Mask.NONE
    }
}
