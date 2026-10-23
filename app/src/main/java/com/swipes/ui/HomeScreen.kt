package com.swipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swipes.capture.CaptureEngine
import com.swipes.data.AppCount
import com.swipes.data.DailyStatsEntity

@Composable
fun HomeScreen(engine: CaptureEngine.State, setupDone: Boolean, topApps: List<AppCount>, onSetup: () -> Unit, onSettings: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Swipes", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
        }
        StatusCard(engine, setupDone, onSetup)
        engine.today?.let { TodayCard(it) }
        if (topApps.isNotEmpty()) TopAppsCard(topApps)
        val geometry = CaptureEngine.geometry
        if (geometry != null && engine.recent.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Latest touches", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your last ${engine.recent.size} touches, colored by app. Typing and lock-screen touches aren't shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StrokeCanvas(
                        engine.recent.map { it.stroke },
                        engine.recent.map { AppColors.color(it.pkg, dark) },
                        geometry,
                        Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(engine: CaptureEngine.State, setupDone: Boolean, onSetup: () -> Unit) {
    val recording = engine.status == CaptureEngine.Status.RECORDING
    val (title, body) = when {
        recording -> "Recording" to "Touches are being captured in the background."
        !setupDone -> "Not set up yet" to "A few one-time steps are needed before recording can start."
        engine.status == CaptureEngine.Status.NEEDS_WIRELESS_DEBUGGING ->
            "Paused" to "Turn on Wireless debugging (on Wi-Fi) so the touch reader can restart, e.g. after a reboot."
        engine.status == CaptureEngine.Status.NEEDS_PAIRING -> "Paused" to "Pairing with Wireless debugging is needed."
        else -> "Connecting…" to engine.detail
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (recording) SuccessGreen else MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp)) {}
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!recording) Button(onClick = onSetup) { Text(if (setupDone) "Fix" else "Start setup") }
        }
    }
}

@Composable
private fun TodayCard(today: DailyStatsEntity) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Row { StatCell("Taps", "%,d".format(today.taps)); StatCell("Swipes", "%,d".format(today.swipes)); StatCell("Scrolls", "%,d".format(today.scrolls)) }
            Row {
                StatCell("Typing", "%,d".format(today.typing))
                StatCell("Finger travel", formatDistance(today.distanceMm))
                StatCell("Touching", formatDuration(today.touchMs))
            }
        }
    }
}

@Composable
private fun TopAppsCard(apps: List<AppCount>) {
    val context = LocalContext.current
    val pm = context.packageManager
    val dark = isSystemInDarkTheme()
    var colorsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { AppColors.ensureAssigned(context) }
        colorsReady = true
    }
    val max = apps.maxOf { it.count }.toFloat()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Most touched today", style = MaterialTheme.typography.titleMedium)
            apps.forEach { app ->
                val label = remember(app.pkg) { appLabel(pm, app.pkg) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    LinearProgressIndicator(
                        progress = { app.count / max },
                        modifier = Modifier.weight(0.45f),
                        color = if (colorsReady) AppColors.color(app.pkg, dark) else MaterialTheme.colorScheme.primary,
                    )
                    Text("${app.count}", Modifier.weight(0.15f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
