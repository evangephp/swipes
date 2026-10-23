package com.swipes.milestones

import com.swipes.data.DailyStatsEntity
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

enum class Metric(val code: Int, val noun: String) {
    TOUCHES(0, "touches"),
    TAPS(1, "taps"),
    SWIPES(2, "swipes"),
    SCROLLS(3, "scrolls"),
    DISTANCE(4, "of finger travel"),
    TOUCH_TIME(5, "of touching"),
    TYPING(6, "key presses"),
    ;

    companion object {
        fun of(code: Int) = entries.first { it.code == code }
    }
}

enum class Period(val code: Int, val label: String, val phrase: String) {
    DAY(0, "day", "today"),
    WEEK(1, "week", "this week"),
    MONTH(2, "month", "this month"),
    YEAR(3, "year", "this year"),
    ;

    companion object {
        fun of(code: Int) = entries.first { it.code == code }
    }
}

enum class DistanceUnit(val code: String, val meters: Double, val singular: String, val plural: String) {
    MILES("mi", 1609.344, "mile", "miles"),
    KILOMETERS("km", 1000.0, "km", "km"),
    ;

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: default()

        fun default(locale: Locale = Locale.getDefault()) =
            if (locale.country in setOf("US", "LR", "MM", "GB")) MILES else KILOMETERS
    }
}

object Milestones {
    /** Metric value in rule units: counts, distance in the rule's unit, touch time in minutes. */
    fun value(metric: Metric, stats: DailyStatsEntity, unit: DistanceUnit): Double = when (metric) {
        Metric.TOUCHES -> stats.touches.toDouble()
        Metric.TYPING -> stats.typing.toDouble()
        Metric.TAPS -> stats.taps.toDouble()
        Metric.SWIPES -> stats.swipes.toDouble()
        Metric.SCROLLS -> stats.scrolls.toDouble()
        Metric.DISTANCE -> stats.distanceMm / 1000 / unit.meters
        Metric.TOUCH_TIME -> stats.touchMs / 60_000.0
    }

    fun periodStart(period: Period, today: LocalDate, locale: Locale = Locale.getDefault()): LocalDate = when (period) {
        Period.DAY -> today
        Period.WEEK -> today.with(WeekFields.of(locale).dayOfWeek(), 1)
        Period.MONTH -> today.withDayOfMonth(1)
        Period.YEAR -> today.withDayOfYear(1)
    }

    /** Identifies the period instance a milestone fired in, so it fires once per day/week/month/year. */
    fun periodKey(period: Period, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (period) {
        Period.DAY -> "D:$today"
        Period.WEEK -> "W:${periodStart(period, today, locale)}"
        Period.MONTH -> "M:${today.year}-${today.monthValue}"
        Period.YEAR -> "Y:${today.year}"
    }

    fun formatThreshold(metric: Metric, threshold: Double, unit: DistanceUnit): String = when (metric) {
        Metric.DISTANCE -> "${trim(threshold)} ${if (threshold == 1.0) unit.singular else unit.plural}"
        Metric.TOUCH_TIME -> if (threshold >= 60 && threshold % 60 == 0.0) {
            val h = (threshold / 60).toInt()
            "$h ${if (h == 1) "hour" else "hours"}"
        } else {
            "${trim(threshold)} min"
        }
        else -> "%,d".format(threshold.toLong())
    }

    /** Current progress in the same units as [formatThreshold], e.g. "12 min", "0.34 miles". */
    fun formatProgress(metric: Metric, value: Double, unit: DistanceUnit): String = when (metric) {
        Metric.DISTANCE -> "%.2f ${unit.plural}".format(value)
        Metric.TOUCH_TIME -> if (value < 60) "%.0f min".format(value) else "%.1f hours".format(value / 60)
        else -> "%,d".format(value.toLong())
    }

    fun title(metric: Metric, period: Period, threshold: Double, unit: DistanceUnit) =
        "${formatThreshold(metric, threshold, unit)} ${metric.noun} in a ${period.label}"

    fun message(metric: Metric, period: Period, threshold: Double, unit: DistanceUnit): String {
        val amount = formatThreshold(metric, threshold, unit)
        val times = "%,d times".format(threshold.toLong())
        return when (metric) {
            Metric.TOUCHES -> "You've touched your screen $times ${period.phrase}."
            Metric.TAPS -> "You've tapped $times ${period.phrase}."
            Metric.SWIPES -> "You've swiped $times ${period.phrase}."
            Metric.SCROLLS -> "You've scrolled $times ${period.phrase}."
            Metric.TYPING -> "You've pressed ${"%,d".format(threshold.toLong())} keys ${period.phrase}."
            Metric.DISTANCE -> "Your finger has traveled $amount on the phone ${period.phrase}."
            Metric.TOUCH_TIME -> "You've spent $amount touching your screen ${period.phrase}."
        }
    }

    private fun trim(v: Double) = if (v % 1.0 == 0.0) "%,d".format(v.toLong()) else "%.1f".format(v)
}
