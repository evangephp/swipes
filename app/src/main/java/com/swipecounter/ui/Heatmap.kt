package com.swipecounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.swipecounter.data.HeatGrid
import kotlin.math.ln

/** Sequential blue ramp, light → dark (dataviz reference palette, steps 100–700). */
private val BLUE_RAMP = listOf(
    0xFFCDE2FB, 0xFFB7D3F6, 0xFF9EC5F4, 0xFF86B6EF, 0xFF6DA7EC, 0xFF5598E7, 0xFF3987E5,
    0xFF2A78D6, 0xFF256ABF, 0xFF1C5CAB, 0xFF184F95, 0xFF104281, 0xFF0D366B,
).map { Color(it) }

/** Low counts recede toward the surface: light steps on a light surface, dark steps on a dark one. */
@Composable
private fun ramp(): List<Color> = if (isSystemInDarkTheme()) BLUE_RAMP.reversed() else BLUE_RAMP

/** Log scale so a few hot spots don't flatten everything else. */
private fun level(count: Int, max: Int, steps: Int): Int =
    if (count <= 0 || max <= 0) -1 else ((ln(1.0 + count) / ln(1.0 + max)) * (steps - 1)).toInt().coerceIn(0, steps - 1)

@Composable
fun Heatmap(
    grid: HeatGrid,
    aspect: Float,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ramp()
    val max = grid.counts.maxOrNull() ?: 0
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .pointerInput(grid) {
                detectTapGestures { p ->
                    val cx = (p.x / size.width * HeatGrid.WIDTH).toInt().coerceIn(0, HeatGrid.WIDTH - 1)
                    val cy = (p.y / size.height * HeatGrid.HEIGHT).toInt().coerceIn(0, HeatGrid.HEIGHT - 1)
                    onSelect(if (selected == cx to cy) null else cx to cy)
                }
            },
    ) {
        val cw = size.width / HeatGrid.WIDTH
        val ch = size.height / HeatGrid.HEIGHT
        for (cy in 0 until HeatGrid.HEIGHT) {
            for (cx in 0 until HeatGrid.WIDTH) {
                val l = level(grid.counts[cy * HeatGrid.WIDTH + cx], max, colors.size)
                if (l < 0) continue
                // Slight overlap hides hairline seams between cells.
                drawRect(colors[l], Offset(cx * cw, cy * ch), Size(cw + 0.5f, ch + 0.5f))
            }
        }
        selected?.let { (cx, cy) ->
            drawRect(outline, Offset(cx * cw - 1, cy * ch - 1), Size(cw + 2, ch + 2), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun HeatmapLegend(max: Int, unit: String, modifier: Modifier = Modifier) {
    val colors = ramp()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(colors)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("1 $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("log scale", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("%,d".format(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
