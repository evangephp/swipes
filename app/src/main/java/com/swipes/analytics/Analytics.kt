package com.swipes.analytics

import com.swipes.capture.CaptureEngine
import com.swipes.capture.Aggregates
import com.swipes.capture.Mask
import com.swipes.capture.POINT_STRIDE
import com.swipes.data.AppDatabase
import com.swipes.data.DailyStatsEntity
import com.swipes.data.HEAT_DOWN
import com.swipes.data.HeatGrid
import com.swipes.data.PointCodec
import com.swipes.data.SessionEntity
import com.swipes.data.StrokeEntity
import java.time.Instant
import java.time.ZoneId

data class AnalyticsResult(
    val grid: HeatGrid,
    val stats: DailyStatsEntity,
    /** Totals per local hour of day (index 0–23) over the range. */
    val byHour: List<DailyStatsEntity>,
    /** Local days the range touches, for per-day averages. */
    val days: Int,
    /** Natural display size of the most recent session, for the phone frame. */
    val frame: SessionEntity?,
    val gaps: Int,
)

/**
 * Heatmaps and totals for a time range. Ranges that line up with local days use the hourly grids
 * and daily stats; anything else (e.g. "last 2 hours") is computed from the strokes themselves.
 */
class Analytics(private val db: AppDatabase) {

    /** Blocking; call off the main thread. */
    fun load(range: TimeRange, heatKind: Int, nowMs: Long, zone: ZoneId): AnalyticsResult {
        CaptureEngine.flushNow()
        val dao = db.captureDao()
        val sessions = dao.sessions().associateBy { it.id }
        val frame = sessions.values.maxByOrNull { it.id }
        val gaps = dao.gapCountBetween(range.startMs, range.endMs)
        val days = TimeRanges.dayCount(range, zone)
        if (!TimeRanges.isDayAligned(range, nowMs, zone)) {
            val strokes = dao.strokesBetween(range.startMs, range.endMs)
            val byHour = hourly(strokes.map { it.startWallMs to delta(it) }, zone)
            return AnalyticsResult(gridOf(strokes, sessions, heatKind), statsOf(strokes), byHour, days, frame, gaps)
        }
        val grid = HeatGrid()
        // Hour buckets are UTC-aligned; local midnights are whole hours in every real zone except a few
        // half-hour offsets, where the edge hour is included.
        val firstHour = range.startMs - Math.floorMod(range.startMs, 3_600_000L)
        for (bucket in dao.heatBuckets(heatKind, firstHour, range.endMs)) grid.addAll(HeatGrid.decode(bucket.grid))
        val (fromDay, toDay) = TimeRanges.dayKeys(range, zone)
        val stats = dao.dailyStatsBetween(fromDay, toDay).fold(DailyStatsEntity("$fromDay..$toDay")) { acc, d -> acc + d.copy(day = acc.day) }
        val byHour = hourly(
            dao.hourStatsBetween(firstHour, range.endMs).map { h ->
                h.hourStartMs to DailyStatsEntity("", h.taps, h.longPresses, h.swipes, h.scrolls, h.multiFinger, h.distanceMm, h.touchMs, h.typing)
            },
            zone,
        )
        return AnalyticsResult(grid, stats, byHour, days, frame, gaps)
    }

    companion object {
        /** Sums (wall time, counters) pairs into 24 local hour-of-day buckets. */
        fun hourly(entries: List<Pair<Long, DailyStatsEntity>>, zone: ZoneId): List<DailyStatsEntity> {
            val hours = MutableList(24) { DailyStatsEntity("") }
            for ((wallMs, d) in entries) {
                val h = Instant.ofEpochMilli(wallMs).atZone(zone).hour
                hours[h] = hours[h] + d
            }
            return hours
        }

        fun gridOf(strokes: List<StrokeEntity>, sessions: Map<Long, SessionEntity>, heatKind: Int): HeatGrid {
            val grid = HeatGrid()
            for (s in strokes) {
                if (s.mask != Mask.NONE.code || s.points == null) continue
                val session = sessions[s.sessionId] ?: continue
                if (heatKind == HEAT_DOWN) {
                    grid.add(s.x0 ?: continue, s.y0 ?: continue, session.panelMaxX, session.panelMaxY)
                    continue
                }
                val points = PointCodec.decode(s.points, s.pointCount)
                for (i in 0 until s.pointCount) {
                    grid.add(points[i * POINT_STRIDE + 1], points[i * POINT_STRIDE + 2], session.panelMaxX, session.panelMaxY)
                }
            }
            return grid
        }

        fun statsOf(strokes: List<StrokeEntity>) = strokes.fold(DailyStatsEntity("")) { acc, s -> acc + delta(s) }

        private fun delta(s: StrokeEntity) =
            Aggregates.delta("", s.kind, s.mask, s.scroll, s.multiFinger && s.firstSeq == s.groupId, s.pathMm.toDouble(), s.durationMs)
    }
}
