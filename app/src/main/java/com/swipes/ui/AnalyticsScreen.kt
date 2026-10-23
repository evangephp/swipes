package com.swipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swipes.analytics.Analytics
import com.swipes.analytics.AnalyticsResult
import com.swipes.analytics.RangePreset
import com.swipes.analytics.TimeRanges
import com.swipes.data.AppDatabase
import com.swipes.data.HEAT_DOWN
import com.swipes.data.HEAT_PATH
import com.swipes.data.HeatGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val analytics = remember { Analytics(AppDatabase.get(context)) }
    var preset by rememberSaveable { mutableStateOf(RangePreset.TODAY) }
    var custom by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var heatKind by rememberSaveable { mutableStateOf(HEAT_DOWN) }
    var result by remember { mutableStateOf<AnalyticsResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(preset, custom, heatKind) {
        loading = true
        selected = null
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        result = withContext(Dispatchers.IO) { analytics.load(TimeRanges.of(preset, now, zone, custom), heatKind, now, zone) }
        loading = false
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Analytics", style = MaterialTheme.typography.headlineMedium)
        RangePicker(preset, custom, onSelect = { p, c -> preset = p; custom = c })
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(HEAT_DOWN to "Touch-downs", HEAT_PATH to "Finger paths").forEachIndexed { i, (kind, label) ->
                SegmentedButton(
                    selected = heatKind == kind,
                    onClick = { heatKind = kind },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        result?.let { r ->
            StatsStrip(r)
            val max = r.grid.counts.maxOrNull() ?: 0
            if (max == 0) {
                Text("No touches recorded in this period.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val unit = if (heatKind == HEAT_DOWN) "touch-down" else "sample"
                val aspect = r.frame?.let { it.naturalWidth.toFloat() / it.naturalHeight } ?: 0.46f
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val hint = selected?.let { (cx, cy) ->
                            val n = r.grid.counts[cy * HeatGrid.WIDTH + cx]
                            "This spot: %,d ${unit}s".format(n)
                        } ?: "Tap the map to see a spot's count."
                        Text(hint, style = MaterialTheme.typography.bodyMedium)
                        Heatmap(r.grid, aspect, selected, { selected = it }, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
                        HeatmapLegend(max, unit, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
                        Text(
                            "Keyboard and lock-screen touches are never stored with positions, so those areas stay blank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (r.gaps > 0) {
                Text(
                    "Recording was interrupted ${r.gaps}× in this period (e.g. after a reboot), so totals may be low.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsStrip(r: AnalyticsResult) {
    val s = r.stats
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                StatCell("Touches", "%,d".format(s.taps + s.longPresses + s.swipes))
                StatCell("Taps", "%,d".format(s.taps))
                StatCell("Scrolls", "%,d".format(s.scrolls))
            }
            Row {
                StatCell("Swipes", "%,d".format(s.swipes))
                StatCell("Finger travel", formatDistance(s.distanceMm))
                StatCell("Touching", formatDuration(s.touchMs))
            }
        }
    }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.StatCell(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatDistance(mm: Double): String = when {
    mm >= 1_000_000 -> "%.2f km".format(mm / 1_000_000)
    mm >= 1000 -> "%.1f m".format(mm / 1000)
    else -> "%.0f cm".format(mm / 10)
}

fun formatDuration(ms: Long): String = when {
    ms >= 3_600_000 -> "%.1f h".format(ms / 3_600_000.0)
    else -> "%.1f min".format(ms / 60_000.0)
}
