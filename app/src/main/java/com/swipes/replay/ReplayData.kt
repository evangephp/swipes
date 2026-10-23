package com.swipes.replay

import androidx.sqlite.db.SimpleSQLiteQuery
import com.swipes.analytics.TimeRange
import com.swipes.capture.CaptureEngine
import com.swipes.capture.Mask
import com.swipes.capture.POINT_STRIDE
import com.swipes.capture.PanelGeometry
import com.swipes.data.AppDatabase
import com.swipes.data.PointCodec

/** A stroke ready to draw: positions normalized to 0..1 of the natural display, with wall times. */
class ReplayStroke(
    val startWallMs: Long,
    val endWallMs: Long,
    val mask: Mask,
    val pkg: String?,
    val xs: FloatArray,
    val ys: FloatArray,
    val timesMs: LongArray,
)

/** Loads replay data from Room. All functions block; call off the main thread. */
class ReplayData(private val db: AppDatabase) {
    private val geometries: Map<Long, PanelGeometry> by lazy {
        db.captureDao().sessions().associate { it.id to PanelGeometry(it.panelMaxX, it.panelMaxY, it.naturalWidth, it.naturalHeight, it.dpi) }
    }

    /**
     * Timeline plus scrub-bar histogram and tally. Streams rows through a cursor (twice: the
     * timeline must exist before strokes can be placed on it) so long ranges never hold every row.
     */
    fun overview(range: TimeRange, condensed: Boolean, bins: Int, isSystem: (String?) -> Boolean): ReplayOverview {
        CaptureEngine.flushNow()
        val query = SimpleSQLiteQuery(
            "SELECT startWallMs, durationMs, pkg FROM stroke WHERE startWallMs >= ? AND startWallMs < ? ORDER BY startWallMs",
            arrayOf(range.startMs, range.endMs),
        )
        val timelineBuilder = ReplayTimeline.Builder()
        db.query(query).use { c ->
            while (c.moveToNext()) timelineBuilder.add(c.getLong(0), c.getLong(0) + c.getLong(1))
        }
        val timeline = timelineBuilder.build(range.startMs, range.endMs, condensed)
        val overview = ReplayOverview.Builder(timeline, bins, isSystem)
        db.query(query).use { c ->
            while (c.moveToNext()) overview.add(c.getLong(0), if (c.isNull(2)) null else c.getString(2))
        }
        return overview.build()
    }

    fun load(fromWallMs: Long, toWallMs: Long): List<ReplayStroke> =
        db.captureDao().strokesBetween(fromWallMs, toWallMs).map { s ->
            val g = geometries[s.sessionId]
            val mask = Mask.entries.first { it.code == s.mask }
            if (s.points == null || g == null) {
                return@map ReplayStroke(s.startWallMs, s.startWallMs + s.durationMs, mask, s.pkg, FloatArray(0), FloatArray(0), LongArray(0))
            }
            val p = PointCodec.decode(s.points, s.pointCount)
            val xs = FloatArray(s.pointCount)
            val ys = FloatArray(s.pointCount)
            val ts = LongArray(s.pointCount)
            var us = 0L
            for (i in 0 until s.pointCount) {
                us += p[i * POINT_STRIDE]
                xs[i] = g.naturalX(p[i * POINT_STRIDE + 1]) / g.naturalWidth
                ys[i] = g.naturalY(p[i * POINT_STRIDE + 2]) / g.naturalHeight
                ts[i] = s.startWallMs + us / 1000
            }
            ReplayStroke(s.startWallMs, s.startWallMs + s.durationMs, mask, s.pkg, xs, ys, ts)
        }
}
