package com.example.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppTimerRuleEntity
import com.example.data.ChapterLockRepository
import com.example.data.DiscoveredDeviceApp
import com.example.data.FocusActiveConfigEntity
import com.example.data.FocusExceptionAppEntity
import com.example.data.FocusHistoryRecordEntity
import com.example.data.PinnedFocusAppEntity
import com.example.service.AppMonitorService
import com.example.service.LockNotificationManager
import com.example.service.LockWindowOverlayManager
import com.example.service.MediaPlaybackSuppressor
import com.example.updater.AppReleaseInfo
import com.example.updater.UpdateState
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    APP_LIMITS,
    FOCUS_LOCK,
    SETTINGS
}

enum class ChallengeMode {
    MEMORY_GRID,
    MATH_CHAIN,
    PHRASE
}

enum class FrictionReason {
    QUIT_FOCUS_LOCK_EARLY,
    UNLOCK_BLOCKED_APP,
    EDIT_LOCKED_APP_SETTINGS
}

data class MathStepProblem(
    val stepIndex: Int,
    val totalSteps: Int = 3,
    val expressionText: String,
    val expectedAnswer: Int
)

data class FrictionChallengeState(
    val reason: FrictionReason,
    val targetAppName: String? = null,
    val targetPackageName: String? = null,
    val wasTriggeredByInterception: Boolean = false,
    val mode: ChallengeMode = ChallengeMode.MEMORY_GRID,
    val patternSequence: List<Int> = emptyList(),
    val userClickedSequence: List<Int> = emptyList(),
    val currentlyFlashingTile: Int? = null,
    val isRevealingPattern: Boolean = true,
    val gridStatusMessage: String = "Memorize the sequence...",
    val mathSteps: List<MathStepProblem> = emptyList(),
    val currentMathStepIndex: Int = 0,
    val mathInput: String = "",
    val mathErrorMessage: String? = null,
    val requiredPhrase: String = "",
    val typedPhrase: String = ""
) {
    val currentMathProblem: MathStepProblem
        get() = mathSteps.getOrElse(currentMathStepIndex) { mathSteps.last() }

    val isPhraseMatched: Boolean
        get() = normalizeText(typedPhrase) == normalizeText(requiredPhrase)

    private fun normalizeText(input: String): String {
        return input.trim().replace(Regex("\\s+"), " ").lowercase()
    }
}

data class ActiveFocusSessionState(
    val isActive: Boolean = false,
    val pinnedAppName: String = "",
    val pinnedPackageName: String = "",
    val selectedDurationMinutes: Int = 45,
    val totalSeconds: Int = 45 * 60,
    val remainingSeconds: Int = 45 * 60
) {
    val progressFraction: Float
        get() = if (totalSeconds > 0) {
            ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedCountdown: String
        get() {
            val hours = remainingSeconds / 3600
            val mins = (remainingSeconds % 3600) / 60
            val secs = remainingSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, mins, secs)
            } else {
                "%02d:%02d".format(mins, secs)
            }
        }
}

data class ChapterLockUiState(
    val currentTab: AppTab = AppTab.APP_LIMITS,
    val appRules: List<AppTimerRuleEntity> = emptyList(),
    val pinnedFocusApps: List<PinnedFocusAppEntity> = emptyList(),
    val focusExceptionApps: List<FocusExceptionAppEntity> = emptyList(),
    val focusConfig: FocusActiveConfigEntity? = null,
    val focusHistory: List<FocusHistoryRecordEntity> = emptyList(),
    val activeSession: ActiveFocusSessionState = ActiveFocusSessionState(),
    val activeChallenge: FrictionChallengeState? = null,
    val editingRule: AppTimerRuleEntity? = null,
    val showAddAppSheet: Boolean = false,
    val showSelectFocusAppSheet: Boolean = false,
    val showSelectFocusExceptionsSheet: Boolean = false,
    val discoveredApps: List<DiscoveredDeviceApp> = emptyList(),
    val hasUsageStatsPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isXiaomiDevice: Boolean = false,
    val showPermissionsSheet: Boolean = false
) {
    val selectedFocusApp: PinnedFocusAppEntity?
        get() = pinnedFocusApps.firstOrNull { it.isSelected } ?: pinnedFocusApps.firstOrNull()

    val allCorePermissionsGranted: Boolean
        get() = hasUsageStatsPermission && hasAccessibilityPermission && hasOverlayPermission

    val missingPermissionsCount: Int
        get() = listOf(hasUsageStatsPermission, hasAccessibilityPermission, hasOverlayPermission)
            .count { !it }
}

private data class InternalControlState(
    val currentTab: AppTab = AppTab.APP_LIMITS,
    val activeChallenge: FrictionChallengeState? = null,
    val editingRule: AppTimerRuleEntity? = null,
    val showAddAppSheet: Boolean = false,
    val showSelectFocusAppSheet: Boolean = false,
    val showSelectFocusExceptionsSheet: Boolean = false,
    val showPermissionsSheet: Boolean = false,
    val discoveredApps: List<DiscoveredDeviceApp> = emptyList(),
    val hasUsageStatsPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isXiaomiDevice: Boolean = false
)

class ChapterLockViewModel(
    private val repository: ChapterLockRepository
) : ViewModel() {

    private val controlState = MutableStateFlow(InternalControlState())
    private var timerJob: Job? = null
    private var patternRevealJob: Job? = null

    private val frictionPhrases = listOf(
        "I am consciously choosing to break my focus lock right now",
        "I choose intentional focus over mindless digital distractions",
        "I am overriding my screen time limit by deliberate choice"
    )

    val uiState: StateFlow<ChapterLockUiState> = combine(
        combine(
            repository.appRulesFlow,
            repository.pinnedFocusAppsFlow,
            repository.focusConfigFlow,
            ::Triple
        ),
        repository.focusHistoryFlow,
        repository.focusExceptionAppsFlow,
        controlState
    ) { (rules, focusApps, focusConfig), history, exceptionApps, ctrl ->
        val isFocusActive = focusConfig?.isStillActive() == true
        val totalSecs = (focusConfig?.selectedDurationMinutes ?: 45) * 60
        val remainingSecs = if (isFocusActive) {
            focusConfig?.remainingSeconds() ?: totalSecs
        } else {
            totalSecs
        }

        val selectedFocus = focusApps.firstOrNull { it.isSelected } ?: focusApps.firstOrNull()
        val resolvedPinnedName = focusConfig?.pinnedAppName?.takeIf { it.isNotBlank() }
            ?: selectedFocus?.appName ?: ""
        val resolvedPinnedPkg = focusConfig?.pinnedPackageName?.takeIf { it.isNotBlank() }
            ?: selectedFocus?.packageName ?: ""

        val activeSession = ActiveFocusSessionState(
            isActive = isFocusActive,
            pinnedAppName = resolvedPinnedName,
            pinnedPackageName = resolvedPinnedPkg,
            selectedDurationMinutes = focusConfig?.selectedDurationMinutes ?: 45,
            totalSeconds = totalSecs,
            remainingSeconds = remainingSecs
        )

        ChapterLockUiState(
            currentTab = ctrl.currentTab,
            appRules = rules,
            pinnedFocusApps = focusApps,
            focusExceptionApps = exceptionApps,
            focusConfig = focusConfig,
            focusHistory = history,
            activeSession = activeSession,
            activeChallenge = ctrl.activeChallenge,
            editingRule = ctrl.editingRule,
            showAddAppSheet = ctrl.showAddAppSheet,
            showSelectFocusAppSheet = ctrl.showSelectFocusAppSheet,
            showSelectFocusExceptionsSheet = ctrl.showSelectFocusExceptionsSheet,
            discoveredApps = ctrl.discoveredApps,
            hasUsageStatsPermission = ctrl.hasUsageStatsPermission,
            hasAccessibilityPermission = ctrl.hasAccessibilityPermission,
            hasOverlayPermission = ctrl.hasOverlayPermission,
            hasNotificationPermission = ctrl.hasNotificationPermission,
            isIgnoringBatteryOptimizations = ctrl.isIgnoringBatteryOptimizations,
            isXiaomiDevice = ctrl.isXiaomiDevice,
            showPermissionsSheet = ctrl.showPermissionsSheet
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChapterLockUiState()
    )

    val updateState: StateFlow<UpdateState> = repository.updateManager.updateState

    init {
        viewModelScope.launch {
            repository.ensureSeededAndSynced()
            refreshPermissionsAndApps()
            startPeriodicTicker()
        }
    }

    fun checkForUpdates(forceUpdate: Boolean = false) {
        viewModelScope.launch {
            repository.updateManager.checkForUpdates(forceUpdate = forceUpdate)
        }
    }

    fun startUpdateDownload(release: AppReleaseInfo) {
        viewModelScope.launch {
            repository.updateManager.startDownload(release)
        }
    }

    fun installUpdateApk(apkFile: File, release: AppReleaseInfo) {
        repository.updateManager.promptInstall(apkFile, release)
    }

    fun dismissUpdateSheet() {
        repository.updateManager.dismiss()
    }

    fun buildUnknownAppSourcesSettingsIntent(): Intent {
        return repository.updateManager.buildUnknownAppSourcesSettingsIntent()
    }

    fun refreshPermissionsAndApps() {
        viewModelScope.launch {
            syncPermissionFlagsOnly()
            repository.syncSystemUsageStats()
            val discovered = repository.discoverCandidateApps()
            controlState.update {
                it.copy(discoveredApps = discovered)
            }
            // Re-check after 400ms in case Settings.canDrawOverlays or AppOps settles asynchronously on return
            delay(400L)
            syncPermissionFlagsOnly()
            repository.updateManager.onAppResumed()
        }
    }

    private fun syncPermissionFlagsOnly() {
        val hasUsage = repository.hasUsageStatsPermission()
        val hasAccess = repository.hasAccessibilityPermission()
        val hasOverlay = repository.hasOverlayPermission()
        val hasNotif = repository.hasNotificationPermission()
        val isIgnoringBattery = repository.isIgnoringBatteryOptimizations()
        val isXiaomi = repository.isXiaomiDevice()
        controlState.update {
            it.copy(
                hasUsageStatsPermission = hasUsage,
                hasAccessibilityPermission = hasAccess,
                hasOverlayPermission = hasOverlay,
                hasNotificationPermission = hasNotif,
                isIgnoringBatteryOptimizations = isIgnoringBattery,
                isXiaomiDevice = isXiaomi
            )
        }
    }

    private fun startPeriodicTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var tick = 0L
            while (true) {
                delay(1000L)
                tick++
                val config = uiState.value.focusConfig
                if (config != null && config.isActive) {
                    if (!config.isStillActive()) {
                        repository.endFocusLockSession(earlyQuit = false)
                    }
                }
                if (tick % 2L == 0L) {
                    syncPermissionFlagsOnly()
                }
                if (tick % 3L == 0L) {
                    repository.syncSystemUsageStats()
                }
            }
        }
    }

    fun handleInterception(packageName: String, reason: String) {
        LockWindowOverlayManager.dismiss()
        val appName = uiState.value.appRules.firstOrNull { it.packageName == packageName }?.appName
            ?: uiState.value.discoveredApps.firstOrNull { it.packageName == packageName }?.appName
            ?: packageName

        val frictionReason = if (reason == "FOCUS_LOCK_BREACH") {
            FrictionReason.QUIT_FOCUS_LOCK_EARLY
        } else {
            FrictionReason.UNLOCK_BLOCKED_APP
        }

        openFrictionChallenge(
            reason = frictionReason,
            targetAppName = appName,
            targetPackageName = packageName,
            wasTriggeredByInterception = true
        )
    }

    fun buildUsageAccessSettingsIntent(): Intent = repository.createUsageAccessIntent()
    fun buildAccessibilitySettingsIntent(): Intent = repository.createAccessibilitySettingsIntent()
    fun buildOverlaySettingsIntent(): Intent = repository.createOverlaySettingsIntent()
    fun buildAppDetailsSettingsIntent(): Intent = repository.createAppDetailsSettingsIntent()
    fun buildNotificationSettingsIntent(): Intent = repository.createNotificationSettingsIntent()
    fun buildBatteryOptimizationSettingsIntent(): Intent = repository.createBatteryOptimizationIntent()
    fun buildXiaomiAutostartIntent(): Intent? = repository.createXiaomiAutostartIntent()
    fun buildLaunchAppIntent(packageName: String): Intent? = repository.createLaunchIntent(packageName)
    fun buildHomeScreenIntent(): Intent = repository.createHomeScreenIntent()

    fun setShowPermissionsSheet(visible: Boolean) {
        controlState.update { it.copy(showPermissionsSheet = visible) }
    }

    fun selectTab(tab: AppTab) {
        controlState.update { it.copy(currentTab = tab) }
    }

    // --- Focus Lock Controls ---

    fun selectFocusDuration(minutes: Int) {
        viewModelScope.launch {
            repository.setFocusDuration(minutes)
        }
    }

    fun startFocusLock(context: Context) {
        val state = uiState.value
        val selected = state.selectedFocusApp
        val currentPkg = selected?.packageName?.takeIf { it.isNotBlank() }
            ?: state.activeSession.pinnedPackageName
        val currentName = selected?.appName?.takeIf { it.isNotBlank() }
            ?: state.activeSession.pinnedAppName
        val currentSub = selected?.subtitle ?: "Installed Focus App"

        if (currentPkg.isBlank()) {
            setShowSelectFocusAppSheet(true)
            return
        }

        if (!state.hasAccessibilityPermission && !state.hasUsageStatsPermission) {
            setShowPermissionsSheet(true)
            return
        }

        viewModelScope.launch {
            val started = repository.startFocusLockSession(
                fallbackPackageName = currentPkg,
                fallbackAppName = currentName,
                fallbackSubtitle = currentSub
            )
            if (started != null) {
                AppMonitorService.start(context)
                LockNotificationManager.refreshForegroundNotification(context)
                // Launch the pinned app directly so the user is immediately locked into it
                try {
                    repository.createLaunchIntent(started.pinnedPackageName)?.let { launchIntent ->
                        context.startActivity(launchIntent)
                    }
                } catch (_: Exception) {
                }
            } else {
                setShowSelectFocusAppSheet(true)
            }
        }
    }

    fun extendActiveFocusLock(context: Context, extraMinutes: Int = 15) {
        viewModelScope.launch {
            val updated = repository.extendActiveFocusSession(extraMinutes)
            if (updated != null) {
                LockNotificationManager.refreshForegroundNotification(context)
            }
        }
    }

    fun launchPinnedFocusApp(context: Context) {
        val pkg = uiState.value.activeSession.pinnedPackageName
            .ifBlank { uiState.value.selectedFocusApp?.packageName.orEmpty() }
        if (pkg.isNotBlank()) {
            try {
                repository.createLaunchIntent(pkg)?.let { launchIntent ->
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun launchAllowedApp(context: Context, packageName: String) {
        if (packageName.isNotBlank()) {
            try {
                repository.createLaunchIntent(packageName)?.let { launchIntent ->
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun requestQuitFocusLockEarly() {
        if (controlState.value.activeChallenge?.reason == FrictionReason.QUIT_FOCUS_LOCK_EARLY) {
            return
        }
        val session = uiState.value.activeSession
        openFrictionChallenge(
            reason = FrictionReason.QUIT_FOCUS_LOCK_EARLY,
            targetAppName = session.pinnedAppName,
            targetPackageName = session.pinnedPackageName,
            wasTriggeredByInterception = false
        )
    }

    fun setShowSelectFocusAppSheet(visible: Boolean) {
        controlState.update { it.copy(showSelectFocusAppSheet = visible) }
    }

    fun setShowSelectFocusExceptionsSheet(visible: Boolean) {
        controlState.update { it.copy(showSelectFocusExceptionsSheet = visible) }
    }

    fun toggleFocusExceptionApp(packageName: String, appName: String, category: String) {
        viewModelScope.launch {
            repository.toggleFocusExceptionApp(packageName, appName, category)
        }
    }

    fun removeFocusExceptionApp(packageName: String) {
        viewModelScope.launch {
            repository.removeFocusExceptionApp(packageName)
        }
    }

    fun selectPinnedFocusApp(packageName: String) {
        viewModelScope.launch {
            repository.selectPinnedFocusApp(packageName)
            controlState.update { it.copy(showSelectFocusAppSheet = false) }
        }
    }

    fun addAndSelectCustomFocusApp(packageName: String, appName: String, subtitle: String) {
        viewModelScope.launch {
            repository.addAndSelectPinnedFocusApp(packageName, appName, subtitle)
            controlState.update { it.copy(showSelectFocusAppSheet = false) }
        }
    }

    // --- App Limits Controls ---

    fun onTapAppRuleCard(rule: AppTimerRuleEntity) {
        val isBlocked = rule.isCurrentlyBlocked(uiState.value.activeSession.isActive)
        if (isBlocked) {
            openFrictionChallenge(
                reason = FrictionReason.EDIT_LOCKED_APP_SETTINGS,
                targetAppName = rule.appName,
                targetPackageName = rule.packageName,
                wasTriggeredByInterception = false
            )
        } else {
            controlState.update { it.copy(editingRule = rule) }
        }
    }

    fun dismissEditRuleSheet() {
        controlState.update { it.copy(editingRule = null) }
    }

    fun updateAppDailyLimit(packageName: String, dailyLimitMinutes: Int) {
        viewModelScope.launch {
            repository.updateAppDailyLimit(packageName, dailyLimitMinutes)
            controlState.update { it.copy(editingRule = null) }
        }
    }

    fun removeAppRule(packageName: String) {
        viewModelScope.launch {
            repository.removeAppRule(packageName)
            controlState.update { it.copy(editingRule = null) }
        }
    }

    fun setShowAddAppSheet(visible: Boolean) {
        controlState.update { it.copy(showAddAppSheet = visible) }
    }

    fun addOrUpdateAppLimitRule(
        packageName: String,
        appName: String,
        category: String,
        dailyLimitMinutes: Int
    ) {
        viewModelScope.launch {
            repository.addOrUpdateAppRule(
                packageName = packageName,
                appName = appName,
                category = category,
                dailyLimitMinutes = dailyLimitMinutes
            )
            val updatedDiscovered = repository.discoverCandidateApps()
            controlState.update {
                it.copy(
                    showAddAppSheet = false,
                    discoveredApps = updatedDiscovered
                )
            }
        }
    }

    // --- Hard Multi-Stage Puzzle Gate ---

    fun openFrictionChallenge(
        reason: FrictionReason,
        targetAppName: String?,
        targetPackageName: String?,
        wasTriggeredByInterception: Boolean = false
    ) {
        val sequence = (0 until 16).shuffled().take(5)
        val mathChain = generateHardMathChain()
        val phrase = frictionPhrases.random()

        controlState.update {
            it.copy(
                activeChallenge = FrictionChallengeState(
                    reason = reason,
                    targetAppName = targetAppName,
                    targetPackageName = targetPackageName,
                    wasTriggeredByInterception = wasTriggeredByInterception,
                    mode = ChallengeMode.MEMORY_GRID,
                    patternSequence = sequence,
                    userClickedSequence = emptyList(),
                    currentlyFlashingTile = null,
                    isRevealingPattern = true,
                    gridStatusMessage = "Memorize the 5-tile sequence...",
                    mathSteps = mathChain,
                    currentMathStepIndex = 0,
                    mathInput = "",
                    mathErrorMessage = null,
                    requiredPhrase = phrase,
                    typedPhrase = ""
                )
            )
        }
        startMemoryPatternReveal(sequence)
    }

    private fun startMemoryPatternReveal(sequence: List<Int>) {
        patternRevealJob?.cancel()
        patternRevealJob = viewModelScope.launch {
            delay(400L)
            for (tileIndex in sequence) {
                controlState.update { state ->
                    val ch = state.activeChallenge ?: return@update state
                    state.copy(
                        activeChallenge = ch.copy(
                            currentlyFlashingTile = tileIndex,
                            isRevealingPattern = true,
                            gridStatusMessage = "Memorize the sequence..."
                        )
                    )
                }
                delay(500L)
                controlState.update { state ->
                    val ch = state.activeChallenge ?: return@update state
                    state.copy(activeChallenge = ch.copy(currentlyFlashingTile = null))
                }
                delay(150L)
            }
            controlState.update { state ->
                val ch = state.activeChallenge ?: return@update state
                state.copy(
                    activeChallenge = ch.copy(
                        isRevealingPattern = false,
                        currentlyFlashingTile = null,
                        gridStatusMessage = "Tap 5 tiles in order"
                    )
                )
            }
        }
    }

    fun replayMemoryPattern() {
        val newSeq = (0 until 16).shuffled().take(5)
        controlState.update { state ->
            val ch = state.activeChallenge ?: return@update state
            state.copy(
                activeChallenge = ch.copy(
                    patternSequence = newSeq,
                    userClickedSequence = emptyList(),
                    isRevealingPattern = true,
                    gridStatusMessage = "Memorize the new sequence..."
                )
            )
        }
        startMemoryPatternReveal(newSeq)
    }

    fun onTapMemoryTile(tileIndex: Int) {
        val ch = controlState.value.activeChallenge ?: return
        if (ch.isRevealingPattern) return

        val nextStepIndex = ch.userClickedSequence.size
        val expectedTile = ch.patternSequence.getOrNull(nextStepIndex) ?: return

        if (tileIndex == expectedTile) {
            val updatedClicked = ch.userClickedSequence + tileIndex
            if (updatedClicked.size == ch.patternSequence.size) {
                fulfillFrictionChallenge()
            } else {
                controlState.update { state ->
                    state.copy(
                        activeChallenge = ch.copy(
                            userClickedSequence = updatedClicked,
                            gridStatusMessage = "Tap 5 tiles in order (${updatedClicked.size}/${ch.patternSequence.size})"
                        )
                    )
                }
            }
        } else {
            val newSeq = (0 until 16).shuffled().take(5)
            controlState.update { state ->
                state.copy(
                    activeChallenge = ch.copy(
                        patternSequence = newSeq,
                        userClickedSequence = emptyList(),
                        isRevealingPattern = true,
                        gridStatusMessage = "Wrong tile! Watch the new sequence..."
                    )
                )
            }
            startMemoryPatternReveal(newSeq)
        }
    }

    fun switchChallengeMode(mode: ChallengeMode) {
        patternRevealJob?.cancel()
        controlState.update { state ->
            val current = state.activeChallenge ?: return@update state
            state.copy(
                activeChallenge = current.copy(
                    mode = mode,
                    userClickedSequence = emptyList(),
                    mathErrorMessage = null
                )
            )
        }
        if (mode == ChallengeMode.MEMORY_GRID) {
            val seq = controlState.value.activeChallenge?.patternSequence ?: return
            startMemoryPatternReveal(seq)
        }
    }

    fun updateChallengeTypedPhrase(input: String) {
        controlState.update { state ->
            val current = state.activeChallenge ?: return@update state
            state.copy(activeChallenge = current.copy(typedPhrase = input))
        }
    }

    fun updateChallengeMathInput(input: String) {
        val filtered = input.filter { it.isDigit() || it == '-' }.take(6)
        controlState.update { state ->
            val current = state.activeChallenge ?: return@update state
            state.copy(
                activeChallenge = current.copy(
                    mathInput = filtered,
                    mathErrorMessage = null
                )
            )
        }
    }

    fun submitMathStepOrUnlock() {
        val challenge = controlState.value.activeChallenge ?: return
        val problem = challenge.currentMathProblem
        val parsed = challenge.mathInput.toIntOrNull()
        if (parsed == null) {
            controlState.update { state ->
                val current = state.activeChallenge ?: return@update state
                state.copy(
                    activeChallenge = current.copy(
                        mathErrorMessage = "Please enter a valid number."
                    )
                )
            }
            return
        }
        if (parsed != problem.expectedAnswer) {
            val freshChain = generateHardMathChain()
            controlState.update { state ->
                state.copy(
                    activeChallenge = challenge.copy(
                        mathSteps = freshChain,
                        currentMathStepIndex = 0,
                        mathInput = "",
                        mathErrorMessage = "Incorrect. Reset to Step 1."
                    )
                )
            }
            return
        }

        if (challenge.currentMathStepIndex + 1 < challenge.mathSteps.size) {
            controlState.update { state ->
                state.copy(
                    activeChallenge = challenge.copy(
                        currentMathStepIndex = challenge.currentMathStepIndex + 1,
                        mathInput = "",
                        mathErrorMessage = null
                    )
                )
            }
        } else {
            fulfillFrictionChallenge()
        }
    }

    fun confirmPhraseChallengeUnlock() {
        val challenge = controlState.value.activeChallenge ?: return
        if (challenge.isPhraseMatched) {
            fulfillFrictionChallenge()
        }
    }

    private fun fulfillFrictionChallenge() {
        patternRevealJob?.cancel()
        LockWindowOverlayManager.dismiss()
        val challenge = controlState.value.activeChallenge ?: return
        controlState.update { it.copy(activeChallenge = null) }

        when (challenge.reason) {
            FrictionReason.QUIT_FOCUS_LOCK_EARLY -> {
                viewModelScope.launch {
                    repository.endFocusLockSession(earlyQuit = true)
                }
            }
            FrictionReason.UNLOCK_BLOCKED_APP -> {
                val pkg = challenge.targetPackageName
                if (pkg != null) {
                    viewModelScope.launch {
                        repository.grantTemporaryUnlockPass(pkg, passMinutes = 10)
                    }
                }
            }
            FrictionReason.EDIT_LOCKED_APP_SETTINGS -> {
                val pkg = challenge.targetPackageName
                val rule = uiState.value.appRules.firstOrNull { it.packageName == pkg }
                if (rule != null) {
                    controlState.update { it.copy(editingRule = rule) }
                }
            }
        }
    }

    fun dismissFrictionChallengeKeepLocked(context: Context? = null) {
        patternRevealJob?.cancel()
        LockWindowOverlayManager.dismiss()
        val currentChallenge = controlState.value.activeChallenge
        controlState.update { it.copy(activeChallenge = null) }

        if (context != null) {
            val targetPkg = currentChallenge?.targetPackageName
            if (targetPkg != null) {
                MediaPlaybackSuppressor.suppressPlayback(context, targetPkg)
            }

            if (uiState.value.activeSession.isActive) {
                val pinnedPkg = uiState.value.activeSession.pinnedPackageName
                if (pinnedPkg.isNotBlank()) {
                    val launchPinned = repository.createLaunchIntent(pinnedPkg)
                    if (launchPinned != null) {
                        try {
                            context.startActivity(launchPinned)
                        } catch (_: Exception) {
                        }
                    }
                }
                (context as? android.app.Activity)?.finish()
                return
            }

            if (currentChallenge?.wasTriggeredByInterception == true) {
                try {
                    context.startActivity(repository.createHomeScreenIntent())
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun generateHardMathChain(): List<MathStepProblem> {
        val a1 = Random.nextInt(14, 28)
        val b1 = Random.nextInt(4, 8)
        val c1 = Random.nextInt(19, 48)
        val ans1 = (a1 * b1) + c1

        val a2 = Random.nextInt(85, 145)
        val b2 = Random.nextInt(28, 64)
        val c2 = Random.nextInt(2, 5)
        val ans2 = (a2 - b2) * c2

        val a3 = Random.nextInt(16, 29)
        val b3 = Random.nextInt(5, 9)
        val c3 = Random.nextInt(23, 59)
        val ans3 = (a3 * b3) - c3

        return listOf(
            MathStepProblem(1, 3, "($a1 × $b1) + $c1", ans1),
            MathStepProblem(2, 3, "($a2 − $b2) × $c2", ans2),
            MathStepProblem(3, 3, "($a3 × $b3) − $c3", ans3)
        )
    }

    override fun onCleared() {
        timerJob?.cancel()
        patternRevealJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(repository: ChapterLockRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChapterLockViewModel(repository) as T
                }
            }
        }
    }
}
