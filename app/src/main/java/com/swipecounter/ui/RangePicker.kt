package com.swipecounter.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipecounter.analytics.RangePreset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One row of range chips; "Custom" opens a date-range picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePicker(
    selected: RangePreset,
    custom: Pair<LocalDate, LocalDate>?,
    onSelect: (RangePreset, Pair<LocalDate, LocalDate>?) -> Unit,
    presets: List<RangePreset> = RangePreset.entries,
) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { preset ->
            val label = if (preset == RangePreset.CUSTOM && custom != null && selected == preset) {
                "${custom.first.shortLabel()} – ${custom.second.shortLabel()}"
            } else {
                preset.label
            }
            FilterChip(
                selected = preset == selected,
                onClick = { if (preset == RangePreset.CUSTOM) picking = true else onSelect(preset, custom) },
                label = { Text(label) },
            )
        }
    }
    if (picking) {
        // DateRangePicker works in UTC-midnight millis.
        val state = rememberDateRangePickerState(selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
        })
        fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = date(state.selectedStartDateMillis!!)
                        val end = state.selectedEndDateMillis?.let(::date) ?: start
                        picking = false
                        onSelect(RangePreset.CUSTOM, start to end)
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f))
        }
    }
}

private fun LocalDate.shortLabel() = "${month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} $dayOfMonth"
