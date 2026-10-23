package com.swipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipes.BuildConfig
import com.swipes.Prefs
import com.swipes.capture.CaptureEngine
import com.swipes.capture.StatusNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onSetup: () -> Unit, onValidate: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var colorsReset by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                ToggleRow(
                    "Status notification",
                    "Shows recording status and today's counts. Turn off if you prefer the home screen widgets.",
                    settings.statusNotification,
                ) { on ->
                    Prefs.update(context) { it.copy(statusNotification = on) }
                    StatusNotifier.update(CaptureEngine.state.value, force = true)
                }
                ToggleRow("Milestone notifications", "Notify when a milestone you turned on is reached.", settings.milestoneNotifications) { on ->
                    Prefs.update(context) { it.copy(milestoneNotifications = on) }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App colors", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your 8 most-used apps get fixed colors in replay and widgets. Reset to re-pick them from current usage.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { AppColors.reset(context) }
                        colorsReset = true
                    }
                }) { Text(if (colorsReset) "Colors reset" else "Reset app colors") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSetup) { Text("Setup") }
            OutlinedButton(onClick = onValidate) { Text("Check it's working") }
        }
        Text("Swipes ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
