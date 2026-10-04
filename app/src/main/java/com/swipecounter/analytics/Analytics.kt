package com.swipecounter.analytics

import com.swipecounter.capture.CaptureEngine
import com.swipecounter.capture.Kind
import com.swipecounter.capture.Mask
import com.swipecounter.capture.POINT_STRIDE
import com.swipecounter.data.AppDatabase
import com.swipecounter.data.DailyStatsEntity
import com.swipecounter.data.HEAT_DOWN
import com.swipecounter.data.HeatGrid
import com.swipecounter.data.PointCodec
import com.swipecounter.data.SessionEntity
import com.swipecounter.data.StrokeEntity
import java.time.ZoneId

data class AnalyticsResult(
    val grid: HeatGrid,
    val stats: DailyStatsEntity,
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
        if (!TimeRanges.isDayAligned(range, nowMs, zone)) {
            val strokes = dao.strokesBetween(range.startMs, range.endMs)
            return AnalyticsResult(gridOf(strokes, sessions, heatKind), statsOf(strokes), frame, gaps)
        }
        val grid = HeatGrid()
        // Hour buckets are UTC-aligned; local midnights are whole hours in every real zone except a few
        // half-hour offsets, where the edge hour is included.
        val firstHour = range.startMs - Math.floorMod(range.startMs, 3_600_000L)
        for (bucket in dao.heatBuckets(heatKind, firstHour, range.endMs)) grid.addAll(HeatGrid.decode(bucket.grid))
        val (fromDay, toDay) = TimeRanges.dayKeys(range, zone)
        val stats = dao.dailyStatsBetween(fromDay, toDay).fold(DailyStatsEntity("$fromDay..$toDay")) { acc, d -> acc + d.copy(day = acc.day) }
        return AnalyticsResult(grid, stats, frame, gaps)
    }

    companion object {
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

        fun statsOf(strokes: List<StrokeEntity>) = DailyStatsEntity(
            day = "",
            taps = strokes.count { it.kind == Kind.TAP.code },
            longPresses = strokes.count { it.kind == Kind.LONG_PRESS.code },
            swipes = strokes.count { it.kind == Kind.SWIPE.code },
            scrolls = strokes.count { it.scroll },
            multiFinger = strokes.count { it.multiFinger && it.firstSeq == it.groupId },
            distanceMm = strokes.sumOf { it.pathMm.toDouble() },
            touchMs = strokes.sumOf { it.durationMs },
        )
    }
}
