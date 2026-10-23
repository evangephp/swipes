package com.swipes.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipes.adb.Pairing
import com.swipes.capture.CaptureEngine

private class StepInfo(val title: String, val why: String, val how: String? = null, val optional: Boolean = false)

private val STEPS = mapOf(
    Step.NOTIFICATIONS to StepInfo(
        "Allow notifications",
        "Shows recording status and is where you type the pairing code.",
    ),
    Step.ACCESSIBILITY to StepInfo(
        "Turn on the accessibility service",
        "Tells Swipes which app you're in and when the keyboard or lock screen is up, so those touches " +
            "are stored without coordinates. It also keeps recording alive in the background. It never reads text.",
        "Accessibility → Installed apps → Swipes → On. If the switch is greyed out: App info → ⋮ → " +
            "Allow restricted settings, then try again.",
    ),
    Step.DEVELOPER_OPTIONS to StepInfo(
        "Enable Developer options",
        "Raw touch data is only readable through Android's debugging tools.",
        "Settings → About phone → Software information → tap Build number 7 times.",
    ),
    Step.USB_DEBUGGING to StepInfo(
        "Turn on USB debugging",
        "No cable needed. It keeps the touch reader running when Wireless debugging turns off or Wi-Fi drops.",
        "Developer options → USB debugging → On.",
    ),
    Step.WIRELESS_DEBUGGING to StepInfo(
        "Turn on Wireless debugging",
        "Used to start the touch reader after each reboot. Needs Wi-Fi. You can turn it off once recording.",
        "Developer options → Wireless debugging → On.",
    ),
    Step.PAIRED to StepInfo(
        "Pair with Wireless debugging",
        "A one-time pairing so Swipes can start its touch reader on this phone.",
        "Tap Start pairing, then in Settings open Wireless debugging → Pair device with pairing code. Keep that " +
            "dialog open and type the 6-digit code into the Swipes notification.",
    ),
    Step.HELPER to StepInfo(
        "Start the touch reader",
        "Reads the touchscreen and streams touches to the app. Restart it here after a reboot.",
    ),
    Step.BATTERY to StepInfo(
        "Allow unrestricted battery",
        "Recommended. Stops Samsung from putting the app to sleep.",
        "Also: Settings → Battery → Background usage limits → Never sleeping apps → add Swipes.",
        optional = true,
    ),
)

@Composable
fun SetupScreen(checks: Checks, engine: CaptureEngine.State, onValidate: () -> Unit) {
    val firstOpen = Step.entries.firstOrNull { checks[it] != true && !STEPS.getValue(it).optional }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "About 3 minutes, once. After a reboot only the last steps need repeating.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Step.entries.forEachIndexed { i, step ->
            StepCard(i + 1, step, checks[step], expanded = step == firstOpen || (step == Step.BATTERY && checks[step] != true), engine)
        }
        if (SetupChecks.complete(checks)) {
            Button(onClick = onValidate, modifier = Modifier.fillMaxWidth()) { Text("Check it's working") }
        }
    }
}

@Composable
private fun StepCard(number: Int, step: Step, done: Boolean?, expanded: Boolean, engine: CaptureEngine.State) {
    val info = STEPS.getValue(step)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(number, done)
                Spacer(Modifier.width(12.dp))
                Text(info.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (info.optional) Text("Optional", style = MaterialTheme.typography.labelSmall)
            }
            if (expanded) {
                Text(info.why, style = MaterialTheme.typography.bodyMedium)
                info.how?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
                StepActions(step, engine)
            } else if (done == null) {
                Text("Can't check this automatically; make sure it's on.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusDot(number: Int, done: Boolean?) {
    val color = when (done) {
        true -> SuccessGreen
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(shape = CircleShape, color = color, modifier = Modifier.size(28.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (done == true) "✓" else "$number", color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@SuppressLint("BatteryLife") // Personal sideloaded app; exempting itself is the point.
@Composable
private fun StepActions(step: Step, engine: CaptureEngine.State) {
    val context = LocalContext.current
    fun open(action: String, data: Uri? = null) = context.startActivity(Intent(action, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pairingPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Pairing.start(context) }
    val appUri = Uri.parse("package:${context.packageName}")

    when (step) {
        Step.NOTIFICATIONS -> Button(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
        Step.ACCESSIBILITY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Open accessibility") }
            TextButton(onClick = { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri) }) { Text("App info") }
        }
        Step.DEVELOPER_OPTIONS -> Button(onClick = { open(Settings.ACTION_DEVICE_INFO_SETTINGS) }) { Text("Open About phone") }
        Step.USB_DEBUGGING, Step.WIRELESS_DEBUGGING ->
            Button(onClick = { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }) { Text("Open Developer options") }
        Step.PAIRED -> PairingPanel { pairingPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        Step.HELPER -> {
            if (engine.detail.isNotEmpty()) Text(engine.detail, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { CaptureEngine.launchHelper("started from setup") },
                enabled = engine.status != CaptureEngine.Status.LAUNCHING,
            ) { Text(if (engine.status == CaptureEngine.Status.LAUNCHING) "Starting…" else "Start") }
        }
        Step.BATTERY -> Button(onClick = { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri) }) { Text("Allow") }
    }
}

@Composable
private fun PairingPanel(onStart: () -> Unit) {
    val context = LocalContext.current
    val state by Pairing.state.collectAsStateWithLifecycle()
    val message = when (val s = state) {
        Pairing.State.Idle -> null
        Pairing.State.Searching -> "Waiting for the pairing dialog in Settings…"
        Pairing.State.AwaitingCode -> "Pairing dialog found. Enter the code in the notification, or below if you use split screen."
        Pairing.State.Pairing -> "Pairing…"
        Pairing.State.Paired -> "Paired."
        is Pairing.State.Failed -> s.message
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart) { Text(if (state == Pairing.State.Idle) "Start pairing" else "Restart") }
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Open Developer options") }
    }
    if (state == Pairing.State.AwaitingCode || state is Pairing.State.Failed) {
        var code by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("Pairing code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { Pairing.onCode(context.applicationContext, code) }, enabled = code.length == 6) { Text("Pair") }
        }
    }
}
