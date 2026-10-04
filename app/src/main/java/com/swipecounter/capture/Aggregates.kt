package com.swipecounter.capture

import com.swipecounter.data.DailyStatsEntity
import com.swipecounter.data.HEAT_DOWN
import com.swipecounter.data.HEAT_PATH
import com.swipecounter.data.HeatGrid
import java.time.Instant
import java.time.ZoneId

/** A finalized stroke plus what's needed to roll it into daily stats and heat grids. */
class RecordedStroke(
    val stroke: Stroke,
    val summary: StrokeSummary,
    val mask: Mask,
    val geometry: PanelGeometry,
)

object Aggregates {
    private const val HOUR_MS = 3_600_000L

    fun dayKey(wallMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate().toString()

    fun dailyDelta(r: RecordedStroke, zone: ZoneId) = DailyStatsEntity(
        day = dayKey(r.stroke.startWallUs / 1000, zone),
        taps = if (r.summary.kind == Kind.TAP) 1 else 0,
        longPresses = if (r.summary.kind == Kind.LONG_PRESS) 1 else 0,
        swipes = if (r.summary.kind == Kind.SWIPE) 1 else 0,
        scrolls = if (r.summary.scroll) 1 else 0,
        // Count each multi-finger gesture once, on its first stroke.
        multiFinger = if (r.stroke.multiFinger && r.stroke.firstSeq == r.stroke.groupId) 1 else 0,
        distanceMm = r.summary.pathMm.toDouble(),
        touchMs = r.stroke.durationMs,
    )

    /** Adds an unmasked stroke to per-UTC-hour grids keyed by (hourStartMs, kind). */
    fun addHeat(r: RecordedStroke, grids: MutableMap<Pair<Long, Int>, HeatGrid>) {
        if (r.mask != Mask.NONE) return
        val wallMs = r.stroke.startWallUs / 1000
        val hour = wallMs - Math.floorMod(wallMs, HOUR_MS)
        val g = r.geometry
        val s = r.stroke
        grids.getOrPut(hour to HEAT_DOWN) { HeatGrid() }.add(s.x(0), s.y(0), g.panelMaxX, g.panelMaxY)
        val path = grids.getOrPut(hour to HEAT_PATH) { HeatGrid() }
        for (i in 0 until s.pointCount) path.add(s.x(i), s.y(i), g.panelMaxX, g.panelMaxY)
    }
}
