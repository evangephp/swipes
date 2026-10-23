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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.swipes.replay.ReplayOverview
import com.swipes.replay.TallyCategory
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
    val controller = remember {
        ReplayController(ReplayData(AppDatabase.get(context)), scope, prepare = { AppColors.ensureAssigned(context) }, isSystem = AppColors::isSystem)
    }
    val label = rememberAppLabels()
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
        val overview = controller.overview
        val timeline = overview?.timeline
        if (overview == null || timeline == null) {
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
            current?.pkg?.let { pkg -> Text(label(pkg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Frame sized by height and flush left, tally takes the rest, so the pair spans the width evenly.
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReplayCanvas(controller, wall)
            TallyPanel(overview, controller.playMs, label, Modifier.weight(1f).fillMaxHeight())
        }
        ScrubBar(overview, controller.playMs, controller::seek)
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
    val timeline = controller.timeline ?: return
    val aspect = CaptureEngine.geometry?.let { it.naturalWidth.toFloat() / it.naturalHeight } ?: 0.46f
    val playMs = controller.playMs
    // Thin out points when many strokes fly by per frame.
    val step = if (controller.speed > 16f) 3 else 1
    val dark = isSystemInDarkTheme()
    Canvas(
        Modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
    ) {
        for (s in controller.strokes) {
            if (s.startWallMs > wall || s.xs.isEmpty()) continue
            val fade = if (s.endWallMs >= wall) 1f else 1f - (playMs - timeline.playAt(s.endWallMs)) / (TRAIL_MS * controller.speed)
            if (fade <= 0f) continue
            drawStroke(s, wall, step, AppColors.color(s.pkg, dark).copy(alpha = fade.coerceIn(0f, 1f)))
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

/** App names are looked up once per package; replay asks every frame. */
@Composable
private fun rememberAppLabels(): (String) -> String {
    val pm = LocalContext.current.packageManager
    val cache = remember { HashMap<String, String>() }
    return { pkg -> cache.getOrPut(pkg) { appLabel(pm, pkg) } }
}

/** Touch-volume histogram over playback time with the played part highlighted; tap or drag to seek. */
@Composable
private fun ScrubBar(overview: ReplayOverview, playMs: Long, onSeek: (Long) -> Unit) {
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    val head = MaterialTheme.colorScheme.onSurface
    val total = overview.timeline.totalMs
    val max = (overview.volume.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(56.dp)
            .pointerInput(overview) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f).times(total).toLong()) } }
            .pointerInput(overview) {
                detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f).times(total).toLong()) }
            },
    ) {
        val headX = if (total > 0) playMs.toFloat() / total * size.width else 0f
        val bw = size.width / overview.bins
        val gap = 1.dp.toPx()
        val chart = size.height - 6.dp.toPx()
        overview.volume.forEachIndexed { b, v ->
            if (v == 0) return@forEachIndexed
            val h = (v.toFloat() / max * chart).coerceAtLeast(1.dp.toPx())
            val x = b * bw
            drawRect(if (x + bw / 2 <= headX) played else rest, Offset(x + gap / 2, chart - h), Size(bw - gap, h))
        }
        drawLine(rest, Offset(0f, chart), Offset(size.width, chart), strokeWidth = 1.dp.toPx())
        drawLine(head, Offset(headX, 0f), Offset(headX, size.height), strokeWidth = 2.dp.toPx())
        drawCircle(head, 5.dp.toPx(), Offset(headX, chart))
    }
}

/** Live per-app touch counts up to the playhead; doubles as the trace color legend. */
@Composable
private fun TallyPanel(overview: ReplayOverview, playMs: Long, label: (String) -> String, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val counts = overview.tallyAt(playMs)
    val order = overview.categories.indices.sortedByDescending { counts[it] }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val listState = rememberLazyListState()
    // A lazy list keeps its first item in view when rows reorder, scrolling the top out of sight; pin it.
    if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) listState.requestScrollToItem(0)
    LazyColumn(modifier, state = listState, userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(order, key = { overview.categories[it].toString() }) { c ->
            val category = overview.categories[c]
            val (name, color) = when (category) {
                is TallyCategory.App -> label(category.pkg) to AppColors.color(category.pkg, dark)
                TallyCategory.System -> "System" to AppColors.color(AppColors.Category.System, dark)
                TallyCategory.Other -> "Other" to AppColors.color(AppColors.Category.Other, dark)
            }
            val fraction by animateFloatAsState(counts[c].toFloat() / max, label = "tally")
            Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%,d".format(counts[c]), style = MaterialTheme.typography.labelMedium)
                }
                Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.02f)).height(8.dp).background(color, RoundedCornerShape(4.dp)))
            }
        }
    }
}

fun appLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))
