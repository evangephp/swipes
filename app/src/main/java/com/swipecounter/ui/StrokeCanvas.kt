package com.swipecounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.unit.dp
import com.swipecounter.capture.PanelGeometry
import com.swipecounter.capture.Stroke

/** Draws strokes on a phone-shaped frame in the natural (portrait) orientation. */
@Composable
fun StrokeCanvas(strokes: List<Stroke>, geometry: PanelGeometry, modifier: Modifier = Modifier) {
    val frame = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .aspectRatio(geometry.naturalWidth.toFloat() / geometry.naturalHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(frame),
    ) {
        val scale = size.width / geometry.naturalWidth
        fun x(s: Stroke, i: Int) = geometry.naturalX(s.x(i)) * scale
        fun y(s: Stroke, i: Int) = geometry.naturalY(s.y(i)) * scale
        strokes.forEachIndexed { index, s ->
            if (s.pointCount == 0) return@forEachIndexed
            // Newest first, fading out.
            val color = ink.copy(alpha = (1f - index / (strokes.size + 2f)).coerceIn(0.2f, 1f))
            if (s.pointCount == 1) {
                drawCircle(color, 5.dp.toPx(), Offset(x(s, 0), y(s, 0)))
                return@forEachIndexed
            }
            val path = Path().apply {
                moveTo(x(s, 0), y(s, 0))
                for (i in 1 until s.pointCount) lineTo(x(s, i), y(s, i))
            }
            drawPath(path, color, style = StrokeStyle(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, 4.dp.toPx(), Offset(x(s, 0), y(s, 0)))
        }
    }
}

val SuccessGreen = Color(0xFF2E7D32)
