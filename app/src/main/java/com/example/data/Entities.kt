package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limit_rules")
data class AppTimerRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val dailyLimitMinutes: Int,
    val usedMinutesToday: Int,
    @ColumnInfo(name = "block_during_reading", defaultValue = "1")
    val blockDuringFocus: Boolean = true,
    val unlockedUntilEpochMs: Long = 0L,
    val lastUpdatedDayEpoch: Long = currentEpochDay()
) {
    val isDailyLimitEnabled: Boolean
        get() = dailyLimitMinutes > 0

    val remainingMinutesToday: Int
        get() = if (dailyLimitMinutes > 0) (dailyLimitMinutes - usedMinutesToday).coerceAtLeast(0) else Int.MAX_VALUE

    val isOverDailyBudget: Boolean
        get() = dailyLimitMinutes > 0 && usedMinutesToday >= dailyLimitMinutes

    val usageProgress: Float
        get() = if (dailyLimitMinutes > 0) {
            (usedMinutesToday.toFloat() / dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val blockDuringReading: Boolean
        get() = blockDuringFocus

    fun isTemporarilyUnlocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        return unlockedUntilEpochMs > nowMs
    }

    fun isCurrentlyBlocked(isFocusSessionActive: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isTemporarilyUnlocked(nowMs)) return false
        if (isFocusSessionActive && blockDuringFocus) return true
        if (isOverDailyBudget) return true
        return false
    }
}

@Entity(tableName = "pinned_focus_apps")
data class PinnedFocusAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val subtitle: String,
    val isSelected: Boolean = false,
    val totalFocusedMinutes: Int = 0,
    val isInstalled: Boolean = true
)

@Entity(tableName = "focus_active_config")
data class FocusActiveConfigEntity(
    @PrimaryKey val id: Int = 1,
    val pinnedPackageName: String = "",
    val pinnedAppName: String = "",
    val pinnedSubtitle: String = "",
    val selectedDurationMinutes: Int = 45,
    val isActive: Boolean = false,
    val startedAtEpochMs: Long = 0L,
    val endTimeEpochMs: Long = 0L
) {
    fun isStillActive(now: Long = System.currentTimeMillis()): Boolean {
        return isActive && now < endTimeEpochMs
    }

    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int {
        if (!isActive) return selectedDurationMinutes * 60
        val diff = (endTimeEpochMs - now) / 1000L
        return diff.coerceAtLeast(0L).toInt()
    }
}

@Entity(tableName = "focus_history")
data class FocusHistoryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val durationMinutes: Int,
    val completedAtTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_exception_apps")
data class FocusExceptionAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String = "Essential"
)

fun currentEpochDay(): Long {
    val cal = java.util.Calendar.getInstance()
    val localMillis = cal.timeInMillis + cal.timeZone.getOffset(cal.timeInMillis)
    return localMillis / (1000L * 60L * 60L * 24L)
}

fun formatMinutesClean(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}
