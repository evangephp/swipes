package com.swipes.capture

/** A window on screen at some moment, as reported by the accessibility service. */
data class WindowSnap(val layer: Int, val bounds: Bounds, val type: Int, val pkg: String?)

/** Which app a touch belongs to: the topmost window under the finger. */
object Attribution {
    // AccessibilityWindowInfo types that never own a touch for attribution purposes.
    private const val TYPE_INPUT_METHOD = 2
    private const val TYPE_ACCESSIBILITY_OVERLAY = 4
    private const val TYPE_MAGNIFICATION_OVERLAY = 6

    fun packageAt(windows: List<WindowSnap>?, x: Float, y: Float): String? = windows
        ?.filter { it.pkg != null && it.type != TYPE_INPUT_METHOD && it.type != TYPE_ACCESSIBILITY_OVERLAY && it.type != TYPE_MAGNIFICATION_OVERLAY }
        ?.filter { it.bounds.contains(x, y) }
        ?.maxByOrNull { it.layer }
        ?.pkg
}
