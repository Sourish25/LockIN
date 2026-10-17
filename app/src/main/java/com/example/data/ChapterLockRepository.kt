package com.example.data

import android.content.Intent
import com.example.service.ChapterLockEnforcer
import kotlinx.coroutines.flow.Flow

class ChapterLockRepository(
    private val dao: ChapterLockDao,
    private val systemInspector: SystemAppInspector
) {
    val appRulesFlow: Flow<List<AppTimerRuleEntity>> = dao.observeAppRules()
    val pinnedFocusAppsFlow: Flow<List<PinnedFocusAppEntity>> = dao.observePinnedFocusApps()
    val focusConfigFlow: Flow<FocusActiveConfigEntity?> = dao.observeFocusConfig()
    val focusHistoryFlow: Flow<List<FocusHistoryRecordEntity>> = dao.observeFocusHistory()

    fun hasUsageStatsPermission(): Boolean = systemInspector.hasUsageStatsPermission()
    fun hasAccessibilityPermission(): Boolean = systemInspector.hasAccessibilityPermission()
    fun hasOverlayPermission(): Boolean = systemInspector.hasOverlayPermission()
    fun isIgnoringBatteryOptimizations(): Boolean = systemInspector.isIgnoringBatteryOptimizations()
    fun isXiaomiDevice(): Boolean = systemInspector.isXiaomiDevice()

    fun createUsageAccessIntent(): Intent = systemInspector.createUsageAccessSettingsIntent()
    fun createAccessibilitySettingsIntent(): Intent = systemInspector.createAccessibilitySettingsIntent()
    fun createOverlaySettingsIntent(): Intent = systemInspector.createOverlaySettingsIntent()
    fun createBatteryOptimizationIntent(): Intent = systemInspector.createBatteryOptimizationIntent()
    fun createXiaomiAutostartIntent(): Intent? = systemInspector.createXiaomiAutostartIntent()

    fun createLaunchIntent(packageName: String): Intent? =
        systemInspector.createLaunchIntentForPackage(packageName)

    fun discoverCandidateApps(): List<DiscoveredDeviceApp> = systemInspector.discoverCandidateApps()

    fun queryCurrentForegroundPackage(): String? = systemInspector.queryCurrentForegroundPackage()

    /**
     * Synchronizes midnight daily counter resets, real installed reader apps,
     * and real UsageStats without seeding any fake or demo app limits.
     */
    suspend fun ensureSeededAndSynced() {
        val todayEpoch = currentEpochDay()
        val existingRules = dao.getAllAppRulesOnce()

        if (existingRules.isNotEmpty()) {
            // Automatic midnight rollover: if day changed, reset daily counters
            val updated = existingRules.map { rule ->
                if (rule.lastUpdatedDayEpoch != todayEpoch) {
                    rule.copy(
                        usedMinutesToday = 0,
                        unlockedUntilEpochMs = 0L,
                        lastUpdatedDayEpoch = todayEpoch
                    )
                } else {
                    rule
                }
            }
            dao.upsertAppRules(updated)
        }

        // Ensure active focus config row exists without pre-populating any demo or unchosen apps
        val currentConfig = dao.getFocusConfigOnce()
        if (currentConfig == null) {
            val selectedApp = dao.getAllPinnedFocusAppsOnce().firstOrNull { it.isSelected }
            dao.saveFocusConfig(
                FocusActiveConfigEntity(
                    pinnedPackageName = selectedApp?.packageName ?: "",
                    pinnedAppName = selectedApp?.appName ?: "",
                    pinnedSubtitle = selectedApp?.subtitle ?: "",
                    selectedDurationMinutes = 45,
                    isActive = false
                )
            )
        } else if (currentConfig.isActive && !currentConfig.isStillActive()) {
            // Expired while process was inactive
            dao.saveFocusConfig(currentConfig.copy(isActive = false))
            if (currentConfig.pinnedPackageName.isNotBlank()) {
                dao.recordFocusHistory(
                    FocusHistoryRecordEntity(
                        packageName = currentConfig.pinnedPackageName,
                        appName = currentConfig.pinnedAppName,
                        durationMinutes = currentConfig.selectedDurationMinutes
                    )
                )
            }
        }

        syncSystemUsageStats()
        syncEnforcerState()
    }

    suspend fun syncSystemUsageStats() {
        val systemUsageMap = systemInspector.queryTodayUsageMinutesMap()
        val rules = dao.getAllAppRulesOnce()
        val todayEpoch = currentEpochDay()

        var hasChanges = false
        val updated = rules.map { rule ->
            val realMinutes = systemUsageMap[rule.packageName] ?: 0
            if (realMinutes != rule.usedMinutesToday || rule.lastUpdatedDayEpoch != todayEpoch) {
                hasChanges = true
                rule.copy(usedMinutesToday = realMinutes, lastUpdatedDayEpoch = todayEpoch)
            } else {
                rule
            }
        }
        if (hasChanges) {
            dao.upsertAppRules(updated)
        }
        syncEnforcerState()
    }

    private suspend fun syncEnforcerState() {
        val rules = dao.getAllAppRulesOnce()
        val config = dao.getFocusConfigOnce()
        ChapterLockEnforcer.syncRules(rules)
        if (config != null) {
            ChapterLockEnforcer.updateFocusLockState(
                active = config.isStillActive(),
                pinnedPackage = config.pinnedPackageName.takeIf { it.isNotBlank() },
                pinnedName = config.pinnedAppName,
                endTimeMs = config.endTimeEpochMs
            )
        }
    }

    suspend fun updateAppDailyLimit(packageName: String, dailyLimitMinutes: Int) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(existing.copy(dailyLimitMinutes = dailyLimitMinutes.coerceIn(5, 360)))
        syncEnforcerState()
    }

    suspend fun addOrUpdateAppRule(
        packageName: String,
        appName: String,
        category: String,
        dailyLimitMinutes: Int
    ) {
        val systemUsageMap = systemInspector.queryTodayUsageMinutesMap()
        val usedToday = systemUsageMap[packageName] ?: 0
        val existing = dao.getAppRule(packageName)
        val rule = existing?.copy(
            dailyLimitMinutes = dailyLimitMinutes.coerceIn(5, 360),
            usedMinutesToday = usedToday
        ) ?: AppTimerRuleEntity(
            packageName = packageName,
            appName = appName,
            category = category,
            dailyLimitMinutes = dailyLimitMinutes.coerceIn(5, 360),
            usedMinutesToday = usedToday,
            blockDuringReading = true
        )
        dao.upsertAppRule(rule)
        syncEnforcerState()
    }

    suspend fun removeAppRule(packageName: String) {
        dao.deleteAppRule(packageName)
        syncEnforcerState()
    }

    suspend fun grantTemporaryUnlockPass(packageName: String, passMinutes: Int) {
        val existing = dao.getAppRule(packageName) ?: return
        val unlockUntil = System.currentTimeMillis() + passMinutes * 60_000L
        dao.upsertAppRule(existing.copy(unlockedUntilEpochMs = unlockUntil))
        ChapterLockEnforcer.grantTemporaryUnlock(packageName, passMinutes)
    }

    suspend fun selectPinnedFocusApp(packageName: String) {
        dao.clearSelectedFocusApps()
        dao.selectFocusApp(packageName)
        val selected = dao.getAllPinnedFocusAppsOnce().firstOrNull { it.packageName == packageName }
        val config = dao.getFocusConfigOnce() ?: FocusActiveConfigEntity()
        if (selected != null) {
            dao.saveFocusConfig(
                config.copy(
                    pinnedPackageName = selected.packageName,
                    pinnedAppName = selected.appName,
                    pinnedSubtitle = selected.subtitle
                )
            )
        }
        syncEnforcerState()
    }

    suspend fun addAndSelectPinnedFocusApp(
        packageName: String,
        appName: String,
        subtitle: String
    ) {
        dao.clearSelectedFocusApps()
        dao.upsertPinnedFocusApp(
            PinnedFocusAppEntity(
                packageName = packageName,
                appName = appName,
                subtitle = subtitle,
                isSelected = true,
                isInstalled = true
            )
        )
        val config = dao.getFocusConfigOnce() ?: FocusActiveConfigEntity()
        dao.saveFocusConfig(
            config.copy(
                pinnedPackageName = packageName,
                pinnedAppName = appName,
                pinnedSubtitle = subtitle
            )
        )
        syncEnforcerState()
    }

    suspend fun setFocusDuration(minutes: Int) {
        val config = dao.getFocusConfigOnce() ?: FocusActiveConfigEntity()
        if (!config.isActive) {
            dao.saveFocusConfig(config.copy(selectedDurationMinutes = minutes.coerceIn(10, 240)))
        }
    }

    suspend fun startFocusLockSession(): FocusActiveConfigEntity? {
        val config = dao.getFocusConfigOnce() ?: FocusActiveConfigEntity()
        if (config.pinnedPackageName.isBlank()) {
            return null
        }
        val now = System.currentTimeMillis()
        val endTime = now + (config.selectedDurationMinutes * 60_000L)
        val activeConfig = config.copy(
            isActive = true,
            startedAtEpochMs = now,
            endTimeEpochMs = endTime
        )
        dao.saveFocusConfig(activeConfig)
        syncEnforcerState()
        return activeConfig
    }

    suspend fun endFocusLockSession(earlyQuit: Boolean = false) {
        val config = dao.getFocusConfigOnce() ?: return
        if (config.isActive) {
            val elapsedMinutes = if (earlyQuit) {
                ((System.currentTimeMillis() - config.startedAtEpochMs) / 60_000L).coerceAtLeast(1L).toInt()
            } else {
                config.selectedDurationMinutes
            }
            if (config.pinnedPackageName.isNotBlank()) {
                dao.addFocusedMinutes(config.pinnedPackageName, elapsedMinutes)
                dao.recordFocusHistory(
                    FocusHistoryRecordEntity(
                        packageName = config.pinnedPackageName,
                        appName = config.pinnedAppName,
                        durationMinutes = elapsedMinutes
                    )
                )
            }
            dao.saveFocusConfig(config.copy(isActive = false))
            syncEnforcerState()
        }
    }
}
