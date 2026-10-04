package com.swipecounter.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class, StrokeEntity::class, DailyStatsEntity::class,
        HeatBucketEntity::class, CaptureGapEntity::class, MetaEntity::class,
        MilestoneRuleEntity::class, MilestoneFiredEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    abstract fun milestoneDao(): MilestoneDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "swipecounter.db")
                    .build()
                    .also { instance = it }
            }
    }
}
