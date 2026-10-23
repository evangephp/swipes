package com.swipes.capture

import com.swipes.data.DailyStatsEntity
import com.swipes.data.HEAT_DOWN
import com.swipes.data.HEAT_PATH
import com.swipes.data.HeatGrid
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

    fun hourStart(wallMs: Long) = wallMs - Math.floorMod(wallMs, HOUR_MS)

    /**
     * One stroke's contribution to the counters. Keyboard strokes count only as typing, so taps and
     * swipes mean touches on app content. The single definition used live, in analytics and when
     * recomputing history.
     */
    fun delta(
        day: String,
        kind: Int,
        mask: Int,
        scroll: Boolean,
        firstOfMultiFingerGesture: Boolean,
        pathMm: Double,
        durationMs: Long,
    ): DailyStatsEntity {
        val typing = mask == Mask.IME.code
        return DailyStatsEntity(
            day = day,
            taps = if (!typing && kind == Kind.TAP.code) 1 else 0,
            longPresses = if (!typing && kind == Kind.LONG_PRESS.code) 1 else 0,
            swipes = if (!typing && kind == Kind.SWIPE.code) 1 else 0,
            scrolls = if (!typing && scroll) 1 else 0,
            multiFinger = if (!typing && firstOfMultiFingerGesture) 1 else 0,
            distanceMm = pathMm,
            touchMs = durationMs,
            typing = if (typing) 1 else 0,
        )
    }

    fun dailyDelta(r: RecordedStroke, zone: ZoneId) = delta(
        day = dayKey(r.stroke.startWallUs / 1000, zone),
        kind = r.summary.kind.code,
        mask = r.mask.code,
        scroll = r.summary.scroll,
        // Count each multi-finger gesture once, on its first stroke.
        firstOfMultiFingerGesture = r.stroke.multiFinger && r.stroke.firstSeq == r.stroke.groupId,
        pathMm = r.summary.pathMm.toDouble(),
        durationMs = r.stroke.durationMs,
    )

    /** Adds an unmasked stroke to per-UTC-hour grids keyed by (hourStartMs, kind). */
    fun addHeat(r: RecordedStroke, grids: MutableMap<Pair<Long, Int>, HeatGrid>) {
        if (r.mask != Mask.NONE) return
        val hour = hourStart(r.stroke.startWallUs / 1000)
        val g = r.geometry
        val s = r.stroke
        grids.getOrPut(hour to HEAT_DOWN) { HeatGrid() }.add(s.x(0), s.y(0), g.panelMaxX, g.panelMaxY)
        val path = grids.getOrPut(hour to HEAT_PATH) { HeatGrid() }
        for (i in 0 until s.pointCount) path.add(s.x(i), s.y(i), g.panelMaxX, g.panelMaxY)
    }
}
