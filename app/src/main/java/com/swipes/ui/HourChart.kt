package com.swipes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.swipes.data.DailyStatsEntity
import kotlin.math.ceil

private val SERIES = listOf("Taps", "Swipes", "Typing")

private fun DailyStatsEntity.series() = listOf(taps + longPresses, swipes, typing)

/**
 * Stacked bars per local hour of day: taps (incl. long presses), swipes, typing. Values are per-day
 * averages when [days] > 1. Tap a bar to see its numbers.
 */
@Composable
fun HourChart(hours: List<DailyStatsEntity>, days: Int, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val colors = SERIES.indices.map { Palette.categorical(it, dark) }
    val values = hours.map { h -> h.series().map { it.toFloat() / days } }
    val max = values.maxOf { it.sum() }.coerceAtLeast(1f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val step = niceStep(max)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val caption = selected?.let { h ->
            val v = values[h]
            val per = if (days > 1) " (avg per day)" else ""
            "%02d:00–%02d:00 · %,.0f taps · %,.0f swipes · %,.0f typing%s".format(h, (h + 1) % 24, v[0], v[1], v[2], per)
        } ?: if (days > 1) "Average per day over $days days. Tap a bar for details." else "Tap a bar for details."
        Text(caption, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SERIES.forEachIndexed { i, name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(colors[i], RoundedCornerShape(2.dp)))
                    Text(name, style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(220.dp).pointerInput(hours) {
                detectTapGestures { p ->
                    val h = (p.x / size.width * 24).toInt().coerceIn(0, 23)
                    onSelect(if (selected == h) null else h)
                }
            },
        ) {
            val top = ceil(max / step) * step
            val slot = size.width / 24
            val gap = 2.dp.toPx()
            val radius = 4.dp.toPx()
            // Recessive gridlines at nice steps.
            var g = 0f
            while (g <= top) {
                val y = size.height - g / top * size.height
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                g += step
            }
            values.forEachIndexed { h, segs ->
                val x = h * slot + gap / 2
                val w = slot - gap
                var bottom = size.height
                val nonEmpty = segs.indices.filter { segs[it] > 0f }
                for (i in nonEmpty) {
                    val height = segs[i] / top * size.height
                    val segTop = bottom - height
                    val isTop = i == nonEmpty.last()
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                Rect(x, segTop, x + w, bottom),
                                topLeft = if (isTop) CornerRadius(radius) else CornerRadius.Zero,
                                topRight = if (isTop) CornerRadius(radius) else CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero,
                                bottomLeft = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, colors[i].copy(alpha = if (selected == null || selected == h) 1f else 0.35f))
                    // 2px surface gap between stacked segments.
                    if (!isTop) drawRect(surface, Offset(x, segTop - gap / 2), Size(w, gap))
                    bottom = segTop
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18").forEach { label ->
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/** 1, 2 or 5 × 10ⁿ so gridlines land on round numbers. */
private fun niceStep(max: Float): Float {
    val raw = max / 4
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw.toDouble()))).toFloat()
    return listOf(1f, 2f, 5f, 10f).map { it * magnitude }.first { it >= raw }
}
