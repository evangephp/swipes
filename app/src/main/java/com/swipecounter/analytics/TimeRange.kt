package com.swipecounter.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Half-open wall-clock interval [startMs, endMs). */
data class TimeRange(val startMs: Long, val endMs: Long) {
    val durationMs get() = endMs - startMs
}

enum class RangePreset(val label: String) {
    LAST_2H("Last 2 h"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7D("7 days"),
    MONTH("This month"),
    YTD("Year to date"),
    CUSTOM("Custom"),
}

object TimeRanges {
    /** [custom] is an inclusive local-date range, used for [RangePreset.CUSTOM]. */
    fun of(preset: RangePreset, nowMs: Long, zone: ZoneId, custom: Pair<LocalDate, LocalDate>? = null): TimeRange {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (preset) {
            RangePreset.LAST_2H -> TimeRange(nowMs - 2 * 3_600_000L, nowMs)
            RangePreset.TODAY -> TimeRange(startOf(today), nowMs)
            RangePreset.YESTERDAY -> TimeRange(startOf(today.minusDays(1)), startOf(today))
            RangePreset.LAST_7D -> TimeRange(startOf(today.minusDays(6)), nowMs)
            RangePreset.MONTH -> TimeRange(startOf(today.withDayOfMonth(1)), nowMs)
            RangePreset.YTD -> TimeRange(startOf(today.withDayOfYear(1)), nowMs)
            RangePreset.CUSTOM -> {
                val (from, to) = custom ?: (today to today)
                TimeRange(startOf(from), minOf(startOf(to.plusDays(1)), nowMs))
            }
        }
    }

    /** Local dates (yyyy-MM-dd) touched by [range], inclusive, for daily_stats lookups. */
    fun dayKeys(range: TimeRange, zone: ZoneId): Pair<String, String> {
        val from = Instant.ofEpochMilli(range.startMs).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(range.endMs - 1).atZone(zone).toLocalDate()
        return from.toString() to to.toString()
    }

    /** True if [range] starts and ends on local midnights (or now), so daily aggregates cover it exactly. */
    fun isDayAligned(range: TimeRange, nowMs: Long, zone: ZoneId): Boolean {
        fun midnight(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).let { it.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() == ms }
        return midnight(range.startMs) && (range.endMs == nowMs || midnight(range.endMs))
    }
}
