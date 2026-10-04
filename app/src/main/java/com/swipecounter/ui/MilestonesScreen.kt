package com.swipecounter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipecounter.capture.CaptureEngine
import com.swipecounter.data.AppDatabase
import com.swipecounter.data.MilestoneRuleEntity
import com.swipecounter.milestones.DistanceUnit
import com.swipecounter.milestones.Metric
import com.swipecounter.milestones.Milestones
import com.swipecounter.milestones.Period
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MilestonesScreen(engine: CaptureEngine.State) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).milestoneDao() }
    val rules by remember { dao.observeRules() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    fun edit(block: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { block() }
        CaptureEngine.milestonesChanged()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val notificationsAllowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Milestones", style = MaterialTheme.typography.headlineMedium)
        Text("Get a notification when you reach one. Each fires at most once per day, week, month or year.", style = MaterialTheme.typography.bodyMedium)
        if (!notificationsAllowed) {
            Card {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications are off.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                }
            }
        }
        rules.forEach { rule ->
            RuleCard(
                rule,
                engine,
                onToggle = { enabled -> edit { dao.upsertRule(rule.copy(enabled = enabled)) } },
                onDelete = { edit { dao.deleteRule(rule.id) } },
            )
        }
        OutlinedButton(onClick = { adding = true }) {
            Icon(Icons.Filled.Add, null)
            Text("Add milestone", Modifier.padding(start = 8.dp))
        }
    }
    if (adding) {
        AddRuleDialog(onDismiss = { adding = false }, onAdd = { rule ->
            adding = false
            edit { dao.insertRule(rule) }
        })
    }
}

@Composable
private fun RuleCard(rule: MilestoneRuleEntity, engine: CaptureEngine.State, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val metric = Metric.of(rule.metric)
    val period = Period.of(rule.period)
    val unit = DistanceUnit.of(rule.unit)
    val current = engine.periodTotals[period]?.let { Milestones.value(metric, it, unit) } ?: 0.0
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Milestones.title(metric, period, rule.threshold, unit), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            LinearProgressIndicator(progress = { (current / rule.threshold).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(
                "${Milestones.formatProgress(metric, current, unit)} of ${Milestones.formatThreshold(metric, rule.threshold, unit)} ${period.phrase}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (MilestoneRuleEntity) -> Unit) {
    var metric by remember { mutableStateOf(Metric.SCROLLS) }
    var period by remember { mutableStateOf(Period.DAY) }
    var unit by remember { mutableStateOf(DistanceUnit.default()) }
    var threshold by remember { mutableStateOf("") }
    val value = threshold.toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New milestone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What", style = MaterialTheme.typography.labelLarge)
                ChipRow(Metric.entries, metric, { metric = it }) {
                    when (it) {
                        Metric.DISTANCE -> "distance"
                        Metric.TOUCH_TIME -> "touch time"
                        else -> it.noun
                    }
                }
                Text("Per", style = MaterialTheme.typography.labelLarge)
                ChipRow(Period.entries, period, { period = it }) { it.label }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter { c -> c.isDigit() || c == '.' } },
                    label = {
                        Text(
                            when (metric) {
                                Metric.DISTANCE -> "Distance (${unit.plural})"
                                Metric.TOUCH_TIME -> "Minutes"
                                else -> "How many"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (metric == Metric.DISTANCE) ChipRow(DistanceUnit.entries, unit, { unit = it }) { it.plural }
                value?.let { Text(Milestones.title(metric, period, it, unit), style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = {
                onAdd(MilestoneRuleEntity(metric = metric.code, period = period.code, threshold = value!!, unit = unit.code, enabled = true))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    // Wraps onto multiple lines in the dialog's narrow width.
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}
