package com.swipes.capture

import com.swipes.data.AppDatabase
import com.swipes.data.DailyStatsEntity
import com.swipes.data.HeatBucketEntity
import com.swipes.data.HeatGrid
import com.swipes.data.HourStatsEntity
import com.swipes.data.MetaEntity
import com.swipes.data.StrokeEntity
import java.time.ZoneId

/** In-memory buffer of finalized strokes, written with their aggregates in one transaction per flush. */
class Recorder(private val db: AppDatabase) {
    private val pending = ArrayList<Pair<StrokeEntity, RecordedStroke>>()

    val size get() = pending.size

    fun add(entity: StrokeEntity, recorded: RecordedStroke) {
        pending += entity to recorded
    }

    /** Pending strokes as a daily delta, so live counters can include not-yet-flushed data. */
    fun pendingDaily(day: String, zone: ZoneId): DailyStatsEntity =
        pending.map { Aggregates.dailyDelta(it.second, zone) }
            .filter { it.day == day }
            .fold(DailyStatsEntity(day)) { acc, d -> acc + d }

    /**
     * Writes pending strokes, aggregates (only for rows actually inserted, so replayed duplicates
     * don't double count) and the resume point. Returns the number of new strokes.
     */
    fun flush(zone: ZoneId, meta: Map<String, String>): Int {
        val batch = pending.toList()
        val dao = db.captureDao()
        var inserted = 0
        db.runInTransaction {
            val ids = if (batch.isEmpty()) emptyList() else dao.insertStrokes(batch.map { it.first })
            val daily = HashMap<String, DailyStatsEntity>()
            val hourly = HashMap<Long, HourStatsEntity>()
            val heat = HashMap<Pair<Long, Int>, HeatGrid>()
            batch.forEachIndexed { i, (_, recorded) ->
                if (ids[i] == -1L) return@forEachIndexed
                inserted++
                val delta = Aggregates.dailyDelta(recorded, zone)
                daily[delta.day] = (daily[delta.day] ?: DailyStatsEntity(delta.day)) + delta
                val hour = Aggregates.hourStart(recorded.stroke.startWallUs / 1000)
                hourly[hour] = (hourly[hour] ?: HourStatsEntity(hour)) + delta
                Aggregates.addHeat(recorded, heat)
            }
            for (d in daily.values) dao.upsertDailyStats((dao.dailyStats(d.day) ?: DailyStatsEntity(d.day)) + d)
            dao.upsertHourStats(
                hourly.values.map { h ->
                    val existing = dao.hourStats(h.hourStartMs) ?: return@map h
                    existing.copy(
                        taps = existing.taps + h.taps, longPresses = existing.longPresses + h.longPresses,
                        swipes = existing.swipes + h.swipes, scrolls = existing.scrolls + h.scrolls,
                        multiFinger = existing.multiFinger + h.multiFinger, distanceMm = existing.distanceMm + h.distanceMm,
                        touchMs = existing.touchMs + h.touchMs, typing = existing.typing + h.typing,
                    )
                },
            )
            for ((key, grid) in heat) {
                val merged = dao.heatBucket(key.first, key.second)?.let { HeatGrid.decode(it.grid) } ?: HeatGrid()
                merged.addAll(grid)
                dao.upsertHeatBucket(HeatBucketEntity(key.first, key.second, merged.encode()))
            }
            for ((k, v) in meta) dao.upsertMeta(MetaEntity(k, v))
        }
        pending.subList(0, batch.size).clear()
        return inserted
    }
}
