package com.swipecounter.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One helper instance's capture run, with what's needed to map its panel units to the screen. */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val helperInstance: Long,
    val startWallMs: Long,
    val deviceName: String,
    val panelMaxX: Int,
    val panelMaxY: Int,
    val naturalWidth: Int,
    val naturalHeight: Int,
    val dpi: Float,
)

/**
 * A finger contact. Masked strokes keep timing, kind and path length but no coordinates.
 * (helperInstance, firstSeq) is unique so samples replayed after an app restart don't duplicate rows.
 */
@Entity(
    tableName = "stroke",
    indices = [Index(value = ["helperInstance", "firstSeq"], unique = true), Index("startWallMs")],
)
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val helperInstance: Long,
    val firstSeq: Long,
    val startWallMs: Long,
    val startMonoUs: Long,
    val durationMs: Long,
    val slot: Int,
    val groupId: Long,
    val multiFinger: Boolean,
    val kind: Int,
    val scroll: Boolean,
    val mask: Int,
    val pkg: String?,
    val rotation: Int,
    val pathMm: Float,
    val pointCount: Int,
    val x0: Int?,
    val y0: Int?,
    val x1: Int?,
    val y1: Int?,
    /** [PointCodec] BLOB, null when masked. */
    val points: ByteArray?,
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    /** Local date, yyyy-MM-dd. */
    @PrimaryKey val day: String,
    val taps: Int = 0,
    val longPresses: Int = 0,
    val swipes: Int = 0,
    val scrolls: Int = 0,
    val multiFinger: Int = 0,
    val distanceMm: Double = 0.0,
    val touchMs: Long = 0,
) {
    operator fun plus(o: DailyStatsEntity) = DailyStatsEntity(
        day, taps + o.taps, longPresses + o.longPresses, swipes + o.swipes, scrolls + o.scrolls,
        multiFinger + o.multiFinger, distanceMm + o.distanceMm, touchMs + o.touchMs,
    )
}

/** Hourly heat grid; [kind] is [HEAT_DOWN] (touch-down points) or [HEAT_PATH] (every point). */
@Entity(tableName = "heat_bucket", primaryKeys = ["hourStartMs", "kind"])
data class HeatBucketEntity(val hourStartMs: Long, val kind: Int, val grid: ByteArray)

const val HEAT_DOWN = 0
const val HEAT_PATH = 1

/** A period where touches may not have been recorded (helper restarted or buffer overrun). */
@Entity(tableName = "capture_gap")
data class CaptureGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startWallMs: Long,
    val endWallMs: Long,
    val reason: String,
)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "milestone_rule")
data class MilestoneRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.swipecounter.milestones.Metric] code. */
    val metric: Int,
    /** [com.swipecounter.milestones.Period] code. */
    val period: Int,
    /** In rule units: a count, distance in [unit], or minutes. */
    val threshold: Double,
    /** [com.swipecounter.milestones.DistanceUnit] code; only used for distance. */
    val unit: String,
    val enabled: Boolean,
)

/** One row per rule per period instance it fired in (e.g. "D:2026-10-04"), for once-per-period dedupe. */
@Entity(tableName = "milestone_fired", primaryKeys = ["ruleId", "periodKey"])
data class MilestoneFiredEntity(val ruleId: Long, val periodKey: String, val firedWallMs: Long)
