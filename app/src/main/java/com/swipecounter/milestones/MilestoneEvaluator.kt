package com.swipecounter.milestones

import com.swipecounter.data.DailyStatsEntity
import com.swipecounter.data.MilestoneRuleEntity
import java.time.LocalDate
import java.util.Locale

/** A rule that just crossed its threshold in the current period. */
data class Reached(val rule: MilestoneRuleEntity, val periodKey: String)

object MilestoneEvaluator {
    /**
     * Enabled rules whose current-period total meets the threshold and that haven't fired for this
     * period yet. [totals] holds each period's running total; [fired] is (ruleId, periodKey) pairs.
     */
    fun due(
        rules: List<MilestoneRuleEntity>,
        totals: Map<Period, DailyStatsEntity>,
        fired: Set<Pair<Long, String>>,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): List<Reached> = rules.mapNotNull { rule ->
        if (!rule.enabled) return@mapNotNull null
        val period = Period.of(rule.period)
        val total = totals[period] ?: return@mapNotNull null
        val key = Milestones.periodKey(period, today, locale)
        if (rule.id to key in fired) return@mapNotNull null
        val value = Milestones.value(Metric.of(rule.metric), total, DistanceUnit.of(rule.unit))
        if (value >= rule.threshold) Reached(rule, key) else null
    }
}
