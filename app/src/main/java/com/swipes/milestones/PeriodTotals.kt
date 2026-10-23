package com.swipes.milestones

import com.swipes.data.DailyStatsEntity

/**
 * Per-period totals from the database as of [day]. Refuses to answer for another day, so totals
 * from before midnight can never be read under the new day's milestone keys.
 */
class PeriodTotals(val day: String, private val base: Map<Period, DailyStatsEntity>) {
    fun withPending(day: String, pending: DailyStatsEntity): Map<Period, DailyStatsEntity>? =
        if (day != this.day) null else Period.entries.associateWith { (base[it] ?: DailyStatsEntity(day)) + pending }
}
