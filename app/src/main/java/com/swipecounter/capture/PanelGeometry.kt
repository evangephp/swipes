package com.swipecounter.capture

import kotlin.math.hypot

/**
 * Maps touch-panel units to display pixels and millimetres. Panel axes follow the display's
 * natural (rotation 0) orientation and scale linearly onto it, as InputReader does.
 */
data class PanelGeometry(
    val panelMaxX: Int,
    val panelMaxY: Int,
    val naturalWidth: Int,
    val naturalHeight: Int,
    val dpi: Float,
) {
    private val sx = naturalWidth / (panelMaxX + 1f)
    private val sy = naturalHeight / (panelMaxY + 1f)

    fun naturalX(panelX: Int) = panelX * sx

    fun naturalY(panelY: Int) = panelY * sy

    fun mm(dxPanel: Float, dyPanel: Float) = hypot(dxPanel * sx, dyPanel * sy) / dpi * 25.4f

    /** Screen x for a [android.view.Surface] rotation, in the same space as window bounds. */
    fun screenX(panelX: Int, panelY: Int, rotation: Int): Float = when (rotation) {
        1 -> naturalY(panelY)
        2 -> naturalWidth - naturalX(panelX)
        3 -> naturalHeight - naturalY(panelY)
        else -> naturalX(panelX)
    }

    fun screenY(panelX: Int, panelY: Int, rotation: Int): Float = when (rotation) {
        1 -> naturalWidth - naturalX(panelX)
        2 -> naturalHeight - naturalY(panelY)
        3 -> naturalX(panelX)
        else -> naturalY(panelY)
    }
}
