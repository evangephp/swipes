package com.swipes.widget

import android.content.Context
import com.swipes.capture.CaptureEngine
import com.swipes.data.AppDatabase
import com.swipes.data.DailyStatsEntity
import com.swipes.milestones.DistanceUnit
import com.swipes.milestones.Metric
import com.swipes.milestones.Milestones
import com.swipes.milestones.Period
import com.swipes.ui.AppColors
import com.swipes.ui.appLabel
import java.time.LocalDate
import java.time.ZoneId

/** Snapshot of what the widgets show. Loaded in the app process, which stays alive while recording. */
data class WidgetData(
    val today: DailyStatsEntity,
    val topApps: List<TopApp>,
    val milestone: NextMilestone?,
) {
    data class TopApp(val name: String, val count: Int, val pkg: String)
    data class NextMilestone(val title: String, val progress: Float, val caption: String)

    companion object {
        /** Blocking; call off the main thread. */
        fun load(context: Context): WidgetData {
            val db = AppDatabase.get(context)
            val state = CaptureEngine.state.value
            val zone = ZoneId.systemDefault()
            val day = LocalDate.now(zone)
            val today = state.today?.takeIf { it.day == day.toString() } ?: db.captureDao().dailyStats(day.toString()) ?: DailyStatsEntity(day.toString())
            AppColors.ensureAssigned(context)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val pm = context.packageManager
            val top = db.captureDao().topApps(start, 10)
                .filterNot { AppColors.isSystem(it.pkg) }
                .take(3)
                .map { TopApp(appLabel(pm, it.pkg), it.count, it.pkg) }
            return WidgetData(today, top, nextMilestone(db, state.periodTotals))
        }

        /** The enabled milestone closest to completion that isn't reached yet in its current period. */
        private fun nextMilestone(db: AppDatabase, totals: Map<Period, DailyStatsEntity>): NextMilestone? =
            db.milestoneDao().rules().filter { it.enabled }.mapNotNull { rule ->
                val metric = Metric.of(rule.metric)
                val period = Period.of(rule.period)
                val unit = DistanceUnit.of(rule.unit)
                val value = totals[period]?.let { Milestones.value(metric, it, unit) } ?: 0.0
                val progress = (value / rule.threshold).toFloat()
                if (progress >= 1f) return@mapNotNull null
                NextMilestone(
                    Milestones.title(metric, period, rule.threshold, unit),
                    progress,
                    "${Milestones.formatProgress(metric, value, unit)} of ${Milestones.formatThreshold(metric, rule.threshold, unit)} ${period.phrase}",
                )
            }.maxByOrNull { it.progress }
    }
}
