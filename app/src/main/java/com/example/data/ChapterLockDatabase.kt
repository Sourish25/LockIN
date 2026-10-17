package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppTimerRuleEntity::class,
        PinnedFocusAppEntity::class,
        FocusActiveConfigEntity::class,
        FocusHistoryRecordEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class ChapterLockDatabase : RoomDatabase() {
    abstract fun dao(): ChapterLockDao

    companion object {
        @Volatile
        private var INSTANCE: ChapterLockDatabase? = null

        fun getInstance(context: Context): ChapterLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChapterLockDatabase::class.java,
                    "lockin_v1_clean.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
