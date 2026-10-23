package com.swipes.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.swipes.capture.CaptureEngine
import com.swipes.capture.ObservedPoint
import com.swipes.capture.Stroke
import com.swipes.capture.Validation
import com.swipes.capture.ValidationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PASS_MEAN_ERROR_MM = 2f
private const val BACKGROUND_SWIPES = 3

@Composable
fun ValidationScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Check it's working", style = MaterialTheme.typography.headlineMedium)
        BackgroundCheck()
        DrawPadCheck(Modifier.weight(1f))
    }
}

@Composable
private fun DrawPadCheck(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val dpi = LocalContext.current.resources.displayMetrics.xdpi
    val trace = remember { mutableStateListOf<ObservedPoint>() }
    var helperTrace by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var result by remember { mutableStateOf<ValidationResult?>(null) }
    var message by remember { mutableStateOf("Draw a line or a curve in the box. Your line is drawn in white; what the touch reader recorded appears on top in color.") }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun evaluate() = scope.launch {
        message = "Comparing…"
        // Strokes are finalized ~0.5 s after lift.
        delay(1_000)
        val geometry = CaptureEngine.geometry
        val box = coords
        if (geometry == null || box == null) {
            message = "The touch reader isn't connected. Finish setup first."
            return@launch
        }
        val rotation = CaptureEngine.screen.rotation.at(SystemClock.uptimeMillis())
        val strokes = CaptureEngine.state.value.recent.map { it.stroke }
        val r = Validation.compare(trace.toList(), strokes, geometry, rotation)
        result = r
        if (r == null) {
            helperTrace = emptyList()
            message = "The touch reader didn't record this stroke. Check the status on the home screen."
            return@launch
        }
        val matched: Stroke = strokes.filter { it.pointCount > 1 }.minBy { kotlin.math.abs(it.startMonoUs / 1000 - trace.first().timeMs) }
        helperTrace = (0 until matched.pointCount).map {
            box.screenToLocal(Offset(geometry.screenX(matched.x(it), matched.y(it), rotation), geometry.screenY(matched.x(it), matched.y(it), rotation)))
        }
        val meanMm = r.meanErrorPx / dpi * 25.4f
        val pass = meanMm <= PASS_MEAN_ERROR_MM
        message = (if (pass) "✓ Working. " else "✗ Positions don't line up. ") +
            "Average offset ${"%.1f".format(meanMm)} mm (${"%.0f".format(r.meanErrorPx)} px), " +
            "max ${"%.0f".format(r.maxErrorPx)} px, ${r.helperPoints} points at ${"%.0f".format(r.helperHz)} Hz."
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. Accuracy", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            val ink = MaterialTheme.colorScheme.primary
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1B1F))
                    .onGloballyPositioned { coords = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            trace.clear()
                            helperTrace = emptyList()
                            result = null
                            fun add(timeMs: Long, local: Offset) {
                                val screen = coords?.localToScreen(local) ?: return
                                trace += ObservedPoint(timeMs, screen.x, screen.y)
                            }
                            add(down.uptimeMillis, down.position)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.historical.forEach { add(it.uptimeMillis, it.position) }
                                if (change.pressed) add(change.uptimeMillis, change.position)
                                change.consume()
                                if (!change.pressed) break
                            }
                            evaluate()
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val box = coords ?: return@Canvas
                    fun path(points: List<Offset>) = Path().apply {
                        points.firstOrNull()?.let { moveTo(it.x, it.y) }
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val mine = trace.map { box.screenToLocal(Offset(it.x, it.y)) }
                    drawPath(path(mine), Color.White, style = StrokeStyle(width = 8.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path(helperTrace), ink, style = StrokeStyle(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun BackgroundCheck() {
    var startedAt by remember { mutableStateOf(0L) }
    var leftAt by remember { mutableStateOf(0L) }
    var count by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (startedAt > 0 && leftAt == 0L) leftAt = System.currentTimeMillis()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (leftAt > 0) {
            val returnedAt = System.currentTimeMillis()
            scope.launch {
                delay(1_000)
                count = CaptureEngine.strokesStartedBetween(leftAt, returnedAt)
                startedAt = 0
                leftAt = 0
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1. Recording in the background", style = MaterialTheme.typography.titleMedium)
            val text = when {
                startedAt > 0 -> "Now go to your home screen, swipe $BACKGROUND_SWIPES times, and come back."
                count == null -> "Checks that touches in other apps are recorded."
                count!! >= BACKGROUND_SWIPES -> "✓ Recorded $count touches while you were away."
                else -> "✗ Only $count touches recorded while you were away. Check the status on the home screen."
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (startedAt == 0L) {
                Button(onClick = {
                    count = null
                    startedAt = System.currentTimeMillis()
                }, modifier = Modifier.align(Alignment.End)) { Text(if (count == null) "Start" else "Again") }
            }
        }
    }
}
