package com.example.data

import android.content.Intent
import com.example.service.ChapterLockEnforcer
import com.example.service.LockWindowOverlayManager
import com.example.updater.AppUpdateManager
import kotlinx.coroutines.flow.Flow

class ChapterLockRepository(
    private val dao: ChapterLockDao,
    val systemInspector: SystemAppInspector
) {
    val updateManager: AppUpdateManager by lazy {
        AppUpdateManager(systemInspector.context.applicationContext)
    }
    val appRulesFlow: Flow<List<AppTimerRuleEntity>> = dao.observeAppRules()
    val pinnedFocusAppsFlow: Flow<List<PinnedFocusAppEntity>> = dao.observePinnedFocusApps()
    val focusConfigFlow: Flow<FocusActiveConfigEntity?> = dao.observeFocusConfig()
    val focusHistoryFlow: Flow<List<FocusHistoryRecordEntity>> = dao.observeFocusHistory()
    val focusExceptionAppsFlow: Flow<List<FocusExceptionAppEntity>> = dao.observeFocusExceptionApps()

    fun hasUsageStatsPermission(): Boolean = systemInspector.hasUsageStatsPermission()
    fun hasAccessibilityPermission(): Boolean = systemInspector.hasAccessibilityPermission()
    fun hasOverlayPermission(): Boolean = systemInspector.hasOverlayPermission()
    fun hasNotificationPermission(): Boolean = systemInspector.hasNotificationPermission()
    fun isIgnoringBatteryOptimizations(): Boolean = systemInspector.isIgnoringBatteryOptimizations()
    fun isXiaomiDevice(): Boolean = systemInspector.isXiaomiDevice()

    fun createUsageAccessIntent(): Intent {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createUsageAccessSettingsIntent()
    }

    fun createAccessibilitySettingsIntent(): Intent {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createAccessibilitySettingsIntent()
    }

    fun createOverlaySettingsIntent(): Intent {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createOverlaySettingsIntent()
    }

    fun createAppDetailsSettingsIntent(): Intent {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createAppDetailsSettingsIntent()
    }

    fun createNotificationSettingsIntent(): Intent {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createNotificationSettingsIntent()
    }

    fun createBatteryOptimizationIntent(): Intent {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createBatteryOptimizationIntent()
    }

    fun createXiaomiAutostartIntent(): Intent? {
        ChapterLockEnforcer.allowSystemSettingsTemporarily()
        LockWindowOverlayManager.dismiss()
        return systemInspector.createXiaomiAutostartIntent()
    }

    fun createHomeScreenIntent(): Intent = systemInspector.createHomeScreenIntent()

    fun createLaunchIntent(packageName: String): Intent? =
        systemInspector.createLaunchIntentForPackage(packageName)

    fun discoverCandidateApps(): List<DiscoveredDeviceApp> = systemInspector.discoverCandidateApps()

    fun queryCurrentForegroundPackage(): String? = systemInspector.queryCurrentForegroundPackage()

    suspend fun getAllAppRulesOnce(): List<AppTimerRuleEntity> = dao.getAllAppRulesOnce()

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
        val allPinned = dao.getAllPinnedFocusAppsOnce()
        val selectedApp = allPinned.firstOrNull { it.isSelected } ?: allPinned.firstOrNull()
        if (currentConfig == null) {
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
        } else if (currentConfig.pinnedPackageName.isBlank() && selectedApp != null) {
            dao.saveFocusConfig(
                currentConfig.copy(
                    pinnedPackageName = selectedApp.packageName,
                    pinnedAppName = selectedApp.appName,
                    pinnedSubtitle = selectedApp.subtitle
                )
            )
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
        val exceptions = dao.getFocusExceptionAppsOnce()
        ChapterLockEnforcer.syncRules(rules)
        ChapterLockEnforcer.syncFocusExceptions(exceptions.map { it.packageName })
        if (config != null) {
            ChapterLockEnforcer.updateFocusLockState(
                active = config.isStillActive(),
                pinnedPackage = config.pinnedPackageName.takeIf { it.isNotBlank() },
                pinnedName = config.pinnedAppName,
                endTimeMs = config.endTimeEpochMs
            )
        }
    }

    suspend fun toggleFocusExceptionApp(
        packageName: String,
        appName: String,
        category: String
    ) {
        val existing = dao.getFocusExceptionAppsOnce().any { it.packageName == packageName }
        if (existing) {
            dao.deleteFocusExceptionApp(packageName)
        } else {
            dao.upsertFocusExceptionApp(
                FocusExceptionAppEntity(
                    packageName = packageName,
                    appName = appName,
                    category = category
                )
            )
        }
        syncEnforcerState()
    }

    suspend fun removeFocusExceptionApp(packageName: String) {
        dao.deleteFocusExceptionApp(packageName)
        syncEnforcerState()
    }

    suspend fun updateAppDailyLimit(packageName: String, dailyLimitMinutes: Int) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(existing.copy(dailyLimitMinutes = dailyLimitMinutes.coerceIn(1, 360)))
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
            dailyLimitMinutes = dailyLimitMinutes.coerceIn(1, 360),
            usedMinutesToday = usedToday
        ) ?: AppTimerRuleEntity(
            packageName = packageName,
            appName = appName,
            category = category,
            dailyLimitMinutes = dailyLimitMinutes.coerceIn(1, 360),
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
        LockWindowOverlayManager.dismiss()
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
            dao.saveFocusConfig(config.copy(selectedDurationMinutes = minutes.coerceIn(5, 240)))
        }
    }

    suspend fun startFocusLockSession(
        fallbackPackageName: String = "",
        fallbackAppName: String = "",
        fallbackSubtitle: String = "",
        overrideDurationMinutes: Int? = null
    ): FocusActiveConfigEntity? {
        val config = dao.getFocusConfigOnce() ?: FocusActiveConfigEntity()
        val resolvedPkg = config.pinnedPackageName.ifBlank { fallbackPackageName }
        val resolvedName = config.pinnedAppName.ifBlank { fallbackAppName }
        val resolvedSub = config.pinnedSubtitle.ifBlank { fallbackSubtitle }
        val resolvedDuration = (overrideDurationMinutes ?: config.selectedDurationMinutes).coerceIn(5, 240)

        if (resolvedPkg.isBlank()) {
            return null
        }

        val now = System.currentTimeMillis()
        val endTime = now + (resolvedDuration * 60_000L)
        val activeConfig = config.copy(
            pinnedPackageName = resolvedPkg,
            pinnedAppName = resolvedName,
            pinnedSubtitle = resolvedSub,
            selectedDurationMinutes = resolvedDuration,
            isActive = true,
            startedAtEpochMs = now,
            endTimeEpochMs = endTime
        )
        dao.saveFocusConfig(activeConfig)
        ChapterLockEnforcer.updateFocusLockState(
            active = true,
            pinnedPackage = resolvedPkg,
            pinnedName = resolvedName,
            endTimeMs = endTime
        )
        syncEnforcerState()
        return activeConfig
    }

    suspend fun extendActiveFocusSession(extraMinutes: Int = 15): FocusActiveConfigEntity? {
        val config = dao.getFocusConfigOnce() ?: return null
        if (!config.isActive) return null
        val now = System.currentTimeMillis()
        val baseEnd = config.endTimeEpochMs.coerceAtLeast(now)
        val newEnd = baseEnd + (extraMinutes.coerceIn(5, 120) * 60_000L)
        val newTotalMinutes = (config.selectedDurationMinutes + extraMinutes).coerceAtMost(360)
        val updated = config.copy(
            selectedDurationMinutes = newTotalMinutes,
            endTimeEpochMs = newEnd
        )
        dao.saveFocusConfig(updated)
        ChapterLockEnforcer.updateFocusLockState(
            active = true,
            pinnedPackage = updated.pinnedPackageName,
            pinnedName = updated.pinnedAppName,
            endTimeMs = newEnd
        )
        syncEnforcerState()
        return updated
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
            ChapterLockEnforcer.updateFocusLockState(
                active = false,
                pinnedPackage = config.pinnedPackageName,
                pinnedName = config.pinnedAppName,
                endTimeMs = 0L
            )
            LockWindowOverlayManager.dismiss()
            syncEnforcerState()
        }
    }
}
