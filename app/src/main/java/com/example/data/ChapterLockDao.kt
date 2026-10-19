package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterLockDao {

    // --- App Limits ---
    @Query("SELECT * FROM app_limit_rules ORDER BY (usedMinutesToday * 1.0 / CASE WHEN dailyLimitMinutes > 0 THEN dailyLimitMinutes ELSE 9999 END) DESC, appName ASC")
    fun observeAppRules(): Flow<List<AppTimerRuleEntity>>

    @Query("SELECT * FROM app_limit_rules")
    suspend fun getAllAppRulesOnce(): List<AppTimerRuleEntity>

    @Query("SELECT * FROM app_limit_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppRule(packageName: String): AppTimerRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppRule(rule: AppTimerRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppRules(rules: List<AppTimerRuleEntity>)

    @Query("DELETE FROM app_limit_rules WHERE packageName = :packageName")
    suspend fun deleteAppRule(packageName: String)

    // --- Pinned Focus Apps ---
    @Query("SELECT * FROM pinned_focus_apps ORDER BY isSelected DESC, isInstalled DESC, appName ASC")
    fun observePinnedFocusApps(): Flow<List<PinnedFocusAppEntity>>

    @Query("SELECT * FROM pinned_focus_apps")
    suspend fun getAllPinnedFocusAppsOnce(): List<PinnedFocusAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPinnedFocusApps(apps: List<PinnedFocusAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPinnedFocusApp(app: PinnedFocusAppEntity)

    @Query("UPDATE pinned_focus_apps SET isSelected = 0")
    suspend fun clearSelectedFocusApps()

    @Query("UPDATE pinned_focus_apps SET isSelected = 1 WHERE packageName = :packageName")
    suspend fun selectFocusApp(packageName: String)

    @Query("UPDATE pinned_focus_apps SET totalFocusedMinutes = totalFocusedMinutes + :addedMinutes WHERE packageName = :packageName")
    suspend fun addFocusedMinutes(packageName: String, addedMinutes: Int)

    // --- Active Focus Config (survives app death & restart) ---
    @Query("SELECT * FROM focus_active_config WHERE id = 1 LIMIT 1")
    fun observeFocusConfig(): Flow<FocusActiveConfigEntity?>

    @Query("SELECT * FROM focus_active_config WHERE id = 1 LIMIT 1")
    suspend fun getFocusConfigOnce(): FocusActiveConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFocusConfig(config: FocusActiveConfigEntity)

    // --- Focus History ---
    @Query("SELECT * FROM focus_history ORDER BY completedAtTimestamp DESC LIMIT 20")
    fun observeFocusHistory(): Flow<List<FocusHistoryRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordFocusHistory(record: FocusHistoryRecordEntity)

    // --- Focus Lock Allowed Exception Apps (Emergency Calls, GPay, Utilities) ---
    @Query("SELECT * FROM focus_exception_apps ORDER BY appName ASC")
    fun observeFocusExceptionApps(): Flow<List<FocusExceptionAppEntity>>

    @Query("SELECT * FROM focus_exception_apps ORDER BY appName ASC")
    suspend fun getFocusExceptionAppsOnce(): List<FocusExceptionAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusExceptionApp(app: FocusExceptionAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusExceptionApps(apps: List<FocusExceptionAppEntity>)

    @Query("DELETE FROM focus_exception_apps WHERE packageName = :packageName")
    suspend fun deleteFocusExceptionApp(packageName: String)
}
