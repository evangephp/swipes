package com.swipes.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.swipes.R
import com.swipes.analytics.RangePreset
import com.swipes.analytics.TimeRanges
import com.swipes.capture.CaptureEngine
import com.swipes.data.AppDatabase
import com.swipes.replay.ReplayController
import com.swipes.replay.ReplayData
import com.swipes.replay.ReplayStroke
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How long (on screen, at any speed) a finished stroke stays visible as a fading trail. */
private const val TRAIL_MS = 600L
private val SPEEDS = listOf(1f, 4f, 16f, 64f)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d · HH:mm:ss")

@Composable
fun ReplayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { ReplayController(ReplayData(AppDatabase.get(context)), scope) }
    var preset by rememberSaveable { mutableStateOf(RangePreset.TODAY) }
    var custom by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var condensed by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(preset, custom, condensed) {
        controller.open(TimeRanges.of(preset, System.currentTimeMillis(), ZoneId.systemDefault(), custom), condensed)
    }
    LaunchedEffect(controller.playing) {
        if (!controller.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (controller.playing) {
            withFrameNanos { now ->
                controller.advance((now - last) / 1e6)
                last = now
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Replay", style = MaterialTheme.typography.headlineMedium)
        RangePicker(preset, custom, onSelect = { p, c -> preset = p; custom = c })
        val timeline = controller.timeline
        if (timeline == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }
        if (timeline.condensed.not() && condensed) {
            Text("No touches recorded in this period.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val wall = timeline.wallAt(controller.playMs)
        val current = controller.strokes.lastOrNull { it.startWallMs <= wall }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).format(TIME_FORMAT), style = MaterialTheme.typography.titleMedium)
            current?.pkg?.let { pkg -> Text(appLabel(context.packageManager, pkg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ReplayCanvas(controller, wall)
        }
        Slider(
            value = controller.playMs.toFloat(),
            onValueChange = { controller.seek(it.toLong()) },
            valueRange = 0f..timeline.totalMs.coerceAtLeast(1).toFloat(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = {
                if (controller.playMs >= timeline.totalMs) controller.seek(0)
                controller.playing = !controller.playing
            }) {
                if (controller.playing) Icon(painterResource(R.drawable.ic_pause), "Pause") else Icon(Icons.Filled.PlayArrow, "Play")
            }
            SPEEDS.forEach { s ->
                FilterChip(selected = controller.speed == s, onClick = { controller.speed = s }, label = { Text("${s.toInt()}×") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Skip idle time", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = condensed, onCheckedChange = { condensed = it })
        }
    }
}

@Composable
private fun ReplayCanvas(controller: ReplayController, wall: Long) {
    val ink = MaterialTheme.colorScheme.primary
    val timeline = controller.timeline ?: return
    val aspect = CaptureEngine.geometry?.let { it.naturalWidth.toFloat() / it.naturalHeight } ?: 0.46f
    val playMs = controller.playMs
    // Thin out points when many strokes fly by per frame.
    val step = if (controller.speed > 16f) 3 else 1
    Canvas(
        Modifier.aspectRatio(aspect)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
    ) {
        for (s in controller.strokes) {
            if (s.startWallMs > wall || s.xs.isEmpty()) continue
            val fade = if (s.endWallMs >= wall) 1f else 1f - (playMs - timeline.playAt(s.endWallMs)) / (TRAIL_MS * controller.speed)
            if (fade <= 0f) continue
            drawStroke(s, wall, step, ink.copy(alpha = fade.coerceIn(0f, 1f)))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(s: ReplayStroke, wall: Long, step: Int, color: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    var last = 0
    while (last + 1 < s.timesMs.size && s.timesMs[last + 1] <= wall) last++
    if (last == 0) {
        drawCircle(color, 6.dp.toPx(), Offset(s.xs[0] * w, s.ys[0] * h))
        return
    }
    val path = Path().apply {
        moveTo(s.xs[0] * w, s.ys[0] * h)
        var i = step
        while (i < last) {
            lineTo(s.xs[i] * w, s.ys[i] * h)
            i += step
        }
        lineTo(s.xs[last] * w, s.ys[last] * h)
    }
    drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    // Fingertip marker while the finger is down.
    if (s.endWallMs >= wall) drawCircle(color, 9.dp.toPx(), Offset(s.xs[last] * w, s.ys[last] * h))
}

fun appLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))
