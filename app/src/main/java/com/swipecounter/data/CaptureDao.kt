package com.swipecounter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert
    fun insertSession(session: SessionEntity): Long

    /** Returns -1 for rows skipped as duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertStrokes(strokes: List<StrokeEntity>): List<Long>

    @Query("SELECT * FROM daily_stats WHERE day = :day")
    fun dailyStats(day: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats WHERE day = :day")
    fun observeDailyStats(day: String): Flow<DailyStatsEntity?>

    @Upsert
    fun upsertDailyStats(stats: DailyStatsEntity)

    @Query("SELECT * FROM heat_bucket WHERE hourStartMs = :hourStartMs AND kind = :kind")
    fun heatBucket(hourStartMs: Long, kind: Int): HeatBucketEntity?

    @Upsert
    fun upsertHeatBucket(bucket: HeatBucketEntity)

    @Insert
    fun insertGap(gap: CaptureGapEntity)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun meta(key: String): String?

    @Upsert
    fun upsertMeta(meta: MetaEntity)

    @Query("SELECT * FROM session")
    fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM stroke WHERE startWallMs >= :fromMs AND startWallMs < :toMs ORDER BY startWallMs")
    fun strokesBetween(fromMs: Long, toMs: Long): List<StrokeEntity>

    @Query("SELECT * FROM heat_bucket WHERE kind = :kind AND hourStartMs >= :fromMs AND hourStartMs < :toMs")
    fun heatBuckets(kind: Int, fromMs: Long, toMs: Long): List<HeatBucketEntity>

    @Query("SELECT * FROM daily_stats WHERE day >= :fromDay AND day <= :toDay")
    fun dailyStatsBetween(fromDay: String, toDay: String): List<DailyStatsEntity>

    @Query("SELECT COUNT(*) FROM capture_gap WHERE endWallMs > :fromMs AND startWallMs < :toMs")
    fun gapCountBetween(fromMs: Long, toMs: Long): Int

    @Query(
        "SELECT pkg, COUNT(*) AS count FROM stroke WHERE startWallMs >= :fromWallMs AND pkg IS NOT NULL " +
            "GROUP BY pkg ORDER BY count DESC LIMIT :limit",
    )
    fun observeTopApps(fromWallMs: Long, limit: Int): Flow<List<AppCount>>
}

data class AppCount(val pkg: String, val count: Int)

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestone_rule ORDER BY id")
    fun rules(): List<MilestoneRuleEntity>

    @Query("SELECT * FROM milestone_rule ORDER BY id")
    fun observeRules(): Flow<List<MilestoneRuleEntity>>

    @Insert
    fun insertRule(rule: MilestoneRuleEntity): Long

    @Upsert
    fun upsertRule(rule: MilestoneRuleEntity)

    @Query("DELETE FROM milestone_rule WHERE id = :id")
    fun deleteRule(id: Long)

    @Query("SELECT periodKey FROM milestone_fired WHERE ruleId = :ruleId")
    fun firedKeys(ruleId: Long): List<String>

    /** Returns -1 if this rule already fired in this period. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFired(fired: MilestoneFiredEntity): Long
}
