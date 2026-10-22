package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChapterLockDatabase
import com.example.data.ChapterLockRepository
import com.example.data.SystemAppInspector
import com.example.service.AppMonitorService
import com.example.service.ChapterLockEnforcer
import com.example.service.LockNotificationManager
import com.example.service.LockWindowOverlayManager
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.components.AddAppTimerBottomSheet
import com.example.ui.components.EditAppLimitBottomSheet
import com.example.ui.components.ExpressiveCloverShape
import com.example.ui.components.ExpressiveSealShape
import com.example.ui.components.ExpressiveUpdateModalSheet
import com.example.ui.components.FrictionShieldOverlay
import com.example.ui.components.SelectFocusAppBottomSheet
import com.example.ui.components.SelectFocusExceptionsBottomSheet
import com.example.ui.components.SystemPermissionsBottomSheet
import com.example.ui.screens.AppVaultScreen
import com.example.ui.screens.ExpressiveSettingsScreen
import com.example.ui.screens.ReadAndLockScreen
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.expressiveClickable
import com.example.updater.AppUpdateManager
import com.example.updater.UpdateState
import com.example.viewmodel.AppTab
import com.example.viewmodel.ChapterLockUiState
import com.example.viewmodel.ChapterLockViewModel
import com.example.viewmodel.FrictionReason
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FORCE_EARLY_EXIT_CHALLENGE = LockNotificationManager.EXTRA_FORCE_EARLY_EXIT_CHALLENGE
        const val EXTRA_LAUNCH_CHALLENGE_FOR_PKG = ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"

        @Volatile
        var isInForeground: Boolean = false
            private set
    }

    private var viewModelInstance: ChapterLockViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Refresh foreground notification state (active only when Focus Lock countdown is running)
        LockNotificationManager.refreshForegroundNotification(applicationContext)

        setContent {
            val context = LocalContext.current
            val repository = remember {
                val database = ChapterLockDatabase.getInstance(context)
                val inspector = SystemAppInspector(context)
                ChapterLockRepository(database.dao(), inspector)
            }
            val viewModel: ChapterLockViewModel = viewModel(
                factory = ChapterLockViewModel.provideFactory(repository)
            )
            viewModelInstance = viewModel

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var useDynamicWallpaper by rememberSaveable { mutableStateOf(false) }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                viewModel.refreshPermissionsAndApps()
                LockNotificationManager.refreshForegroundNotification(context)
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !repository.hasNotificationPermission()
                ) {
                    try {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (_: Exception) {
                    }
                }
            }

            LaunchedEffect(intent) {
                checkAndHandleIntent(intent, viewModel)
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            isInForeground = true
                            LockWindowOverlayManager.dismiss()
                            viewModel.refreshPermissionsAndApps()
                            LockNotificationManager.refreshForegroundNotification(context)
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            isInForeground = false
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    isInForeground = false
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MyApplicationTheme(dynamicColor = useDynamicWallpaper) {
                ChapterLockRoot(
                    uiState = uiState,
                    viewModel = viewModel,
                    useDynamicWallpaper = useDynamicWallpaper,
                    onToggleWallpaperTheme = { useDynamicWallpaper = !useDynamicWallpaper },
                    onOpenPermissionsSheet = { viewModel.setShowPermissionsSheet(true) },
                    onLaunchUsageSettings = {
                        try {
                            context.startActivity(viewModel.buildUsageAccessSettingsIntent())
                        } catch (_: Exception) {
                        }
                    },
                    onLaunchAccessibilitySettings = {
                        try {
                            context.startActivity(viewModel.buildAccessibilitySettingsIntent())
                        } catch (_: Exception) {
                        }
                    },
                    onLaunchOverlaySettings = {
                        try {
                            context.startActivity(viewModel.buildOverlaySettingsIntent())
                        } catch (_: Exception) {
                        }
                    },
                    onLaunchAppDetailsSettings = {
                        try {
                            context.startActivity(viewModel.buildAppDetailsSettingsIntent())
                        } catch (_: Exception) {
                        }
                    },
                    onLaunchNotificationSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !uiState.hasNotificationPermission
                        ) {
                            try {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(viewModel.buildNotificationSettingsIntent())
                                } catch (_: Exception) {
                                }
                            }
                        } else {
                            try {
                                context.startActivity(viewModel.buildNotificationSettingsIntent())
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onLaunchBatteryOptimization = {
                        try {
                            context.startActivity(viewModel.buildBatteryOptimizationSettingsIntent())
                        } catch (_: Exception) {
                        }
                    },
                    onLaunchXiaomiAutostart = {
                        try {
                            viewModel.buildXiaomiAutostartIntent()?.let { context.startActivity(it) }
                        } catch (_: Exception) {
                        }
                    },
                    onLaunchPinnedApp = { packageName ->
                        try {
                            val launchIntent = viewModel.buildLaunchAppIntent(packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        LockWindowOverlayManager.dismiss()
    }

    override fun onPause() {
        isInForeground = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LockWindowOverlayManager.dismiss()
        viewModelInstance?.let { checkAndHandleIntent(intent, it) }
    }

    private fun checkAndHandleIntent(intent: Intent?, vm: ChapterLockViewModel) {
        if (intent == null) return

        // 1. Check if launched from notification "Unlock (Puzzle)" during Focus Lock
        if (intent.getBooleanExtra(EXTRA_FORCE_EARLY_EXIT_CHALLENGE, false)) {
            vm.selectTab(AppTab.FOCUS_LOCK)
            vm.requestQuitFocusLockEarly()
            intent.removeExtra(EXTRA_FORCE_EARLY_EXIT_CHALLENGE)
            return
        }

        // 2. Check if launched from interception barrier or notification "10m Pass (Puzzle)"
        val interceptedPkg = intent.getStringExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE)
        val interceptReason = intent.getStringExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON)
        if (!interceptedPkg.isNullOrBlank()) {
            vm.handleInterception(interceptedPkg, interceptReason ?: "INTERCEPTED")
            intent.removeExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE)
            intent.removeExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON)
            return
        }

        // 3. Check if launched from notification tab shortcut ("App Limits" / "Focus Lock")
        val targetTab = intent.getStringExtra(LockNotificationManager.EXTRA_OPEN_TAB)
        if (!targetTab.isNullOrBlank()) {
            when (targetTab) {
                LockNotificationManager.TAB_FOCUS_LOCK -> vm.selectTab(AppTab.FOCUS_LOCK)
                LockNotificationManager.TAB_APP_LIMITS -> vm.selectTab(AppTab.APP_LIMITS)
            }
            intent.removeExtra(LockNotificationManager.EXTRA_OPEN_TAB)
        }
    }
}

@Composable
fun ChapterLockRoot(
    uiState: ChapterLockUiState,
    viewModel: ChapterLockViewModel,
    useDynamicWallpaper: Boolean,
    onToggleWallpaperTheme: () -> Unit,
    onOpenPermissionsSheet: () -> Unit,
    onLaunchUsageSettings: () -> Unit,
    onLaunchAccessibilitySettings: () -> Unit,
    onLaunchOverlaySettings: () -> Unit,
    onLaunchAppDetailsSettings: () -> Unit = {},
    onLaunchNotificationSettings: () -> Unit = {},
    onLaunchBatteryOptimization: () -> Unit,
    onLaunchXiaomiAutostart: () -> Unit,
    onLaunchPinnedApp: (String) -> Unit
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    if (uiState.currentTab != AppTab.APP_LIMITS && uiState.activeChallenge == null) {
        BackHandler {
            viewModel.selectTab(AppTab.APP_LIMITS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Use side NavigationRail and 2-pane layout ONLY on wide landscape tablets;
            // vertical/portrait tablets use full horizontal width with the bottom dock so cards never crush.
            val isTablet = maxWidth >= 740.dp && maxWidth > maxHeight

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isTablet) {
                            ExpressiveNavigationRail(
                                currentTab = uiState.currentTab,
                                onSelectTab = viewModel::selectTab,
                                onQuickAddLimit = { viewModel.setShowAddAppSheet(true) }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            ExpressiveTopBar(
                                currentTab = uiState.currentTab,
                                allPermissionsGranted = uiState.allCorePermissionsGranted,
                                isUpdateAvailable = updateState is UpdateState.UpdateAvailable,
                                useDynamicWallpaper = useDynamicWallpaper,
                                isTablet = isTablet,
                                onOpenPermissionsSheet = onOpenPermissionsSheet,
                                onCheckForUpdates = viewModel::checkForUpdates,
                                onOpenSettings = { viewModel.selectTab(AppTab.SETTINGS) },
                                onNavigateBack = if (uiState.currentTab == AppTab.SETTINGS) {
                                    { viewModel.selectTab(AppTab.APP_LIMITS) }
                                } else null,
                                onToggleWallpaperTheme = onToggleWallpaperTheme
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                AnimatedContent(
                                    targetState = uiState.currentTab,
                                    transitionSpec = {
                                        LockInMotion.directionalTabTransition(
                                            movingForward = targetState.ordinal >= initialState.ordinal
                                        )
                                    },
                                    label = "main_tab_spatial_transition"
                                ) { tab ->
                                    when (tab) {
                                        AppTab.APP_LIMITS -> {
                                            AppVaultScreen(
                                                appRules = uiState.appRules,
                                                discoveredApps = uiState.discoveredApps,
                                                isFocusSessionActive = uiState.activeSession.isActive,
                                                activeFocusAppName = uiState.activeSession.pinnedAppName,
                                                activeFocusPackageName = uiState.activeSession.pinnedPackageName,
                                                activeFocusCountdown = uiState.activeSession.formattedCountdown,
                                                hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                                                hasAccessibilityPermission = uiState.hasAccessibilityPermission,
                                                hasOverlayPermission = uiState.hasOverlayPermission,
                                                isTablet = isTablet,
                                                onOpenUsageAccessSettings = onLaunchUsageSettings,
                                                onOpenAccessibilitySettings = onLaunchAccessibilitySettings,
                                                onOpenOverlaySettings = onLaunchOverlaySettings,
                                                onOpenAppDetailsSettings = onLaunchAppDetailsSettings,
                                                onOpenPermissionsSheet = onOpenPermissionsSheet,
                                                onTapAppRule = viewModel::onTapAppRuleCard,
                                                onRequestUnlockApp = { rule ->
                                                    viewModel.openFrictionChallenge(
                                                        reason = FrictionReason.UNLOCK_BLOCKED_APP,
                                                        targetAppName = rule.appName,
                                                        targetPackageName = rule.packageName
                                                    )
                                                },
                                                onQuickAddAppLimit = { app, mins ->
                                                    viewModel.addOrUpdateAppLimitRule(
                                                        packageName = app.packageName,
                                                        appName = app.appName,
                                                        category = app.category,
                                                        dailyLimitMinutes = mins
                                                    )
                                                },
                                                onOpenAddAppSheet = { viewModel.setShowAddAppSheet(true) },
                                                onLaunchPinnedFocusApp = {
                                                    viewModel.launchPinnedFocusApp(context)
                                                },
                                                onExtendFocusLock = {
                                                    viewModel.extendActiveFocusLock(context, 15)
                                                },
                                                onSwitchToFocusTab = {
                                                    viewModel.selectTab(AppTab.FOCUS_LOCK)
                                                }
                                            )
                                        }

                                        AppTab.FOCUS_LOCK -> {
                                            ReadAndLockScreen(
                                                selectedFocusApp = uiState.selectedFocusApp,
                                                activeSession = uiState.activeSession,
                                                focusExceptionApps = uiState.focusExceptionApps,
                                                focusHistoryCount = uiState.focusHistory.size,
                                                isTablet = isTablet,
                                                onSelectDuration = viewModel::selectFocusDuration,
                                                onStartFocusLock = { viewModel.startFocusLock(context) },
                                                onLaunchPinnedApp = onLaunchPinnedApp,
                                                onLaunchExceptionApp = { pkg ->
                                                    viewModel.launchAllowedApp(context, pkg)
                                                },
                                                onExtendFocusLock = {
                                                    viewModel.extendActiveFocusLock(context, 15)
                                                },
                                                onRequestQuitFocusEarly = viewModel::requestQuitFocusLockEarly,
                                                onOpenSelectFocusAppSheet = {
                                                    viewModel.setShowSelectFocusAppSheet(true)
                                                },
                                                onOpenSelectFocusExceptionsSheet = {
                                                    viewModel.setShowSelectFocusExceptionsSheet(true)
                                                }
                                            )
                                        }

                                        AppTab.SETTINGS -> {
                                            ExpressiveSettingsScreen(
                                                hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                                                hasAccessibilityPermission = uiState.hasAccessibilityPermission,
                                                hasOverlayPermission = uiState.hasOverlayPermission,
                                                hasNotificationPermission = uiState.hasNotificationPermission,
                                                isBatteryOptimizedExempt = uiState.isIgnoringBatteryOptimizations,
                                                isXiaomiDevice = uiState.isXiaomiDevice,
                                                focusExceptionApps = uiState.focusExceptionApps,
                                                selectedDurationMinutes = uiState.activeSession.selectedDurationMinutes,
                                                updateState = updateState,
                                                isTablet = isTablet,
                                                onNavigateBackToHome = { viewModel.selectTab(AppTab.APP_LIMITS) },
                                                onOpenUsageAccessSettings = onLaunchUsageSettings,
                                                onOpenAccessibilitySettings = onLaunchAccessibilitySettings,
                                                onOpenOverlaySettings = onLaunchOverlaySettings,
                                                onOpenAppDetailsSettings = onLaunchAppDetailsSettings,
                                                onOpenNotificationSettings = onLaunchNotificationSettings,
                                                onOpenBatteryOptimizationSettings = onLaunchBatteryOptimization,
                                                onOpenXiaomiAutostartSettings = if (uiState.isXiaomiDevice) onLaunchXiaomiAutostart else null,
                                                onOpenExceptionsSheet = { viewModel.setShowSelectFocusExceptionsSheet(true) },
                                                onSelectDurationMinutes = viewModel::selectFocusDuration,
                                                onCheckForUpdates = viewModel::checkForUpdates
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Pill Dock on Phone & Portrait Tablet
                    if (!isTablet && uiState.currentTab != AppTab.SETTINGS) {
                        FloatingExpressivePillDock(
                            currentTab = uiState.currentTab,
                            onSelectTab = viewModel::selectTab,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }

        // Bottom Sheets
        EditAppLimitBottomSheet(
            rule = uiState.editingRule,
            onDismiss = viewModel::dismissEditRuleSheet,
            onSaveLimit = { newMins ->
                uiState.editingRule?.let { rule ->
                    viewModel.updateAppDailyLimit(rule.packageName, newMins)
                }
            },
            onRemoveRule = {
                uiState.editingRule?.let { rule ->
                    viewModel.removeAppRule(rule.packageName)
                }
            }
        )

        AddAppTimerBottomSheet(
            isVisible = uiState.showAddAppSheet,
            discoveredApps = uiState.discoveredApps,
            existingPackages = uiState.appRules.map { it.packageName }.toSet(),
            onDismiss = { viewModel.setShowAddAppSheet(false) },
            onAddAppRule = viewModel::addOrUpdateAppLimitRule
        )

        SelectFocusAppBottomSheet(
            isVisible = uiState.showSelectFocusAppSheet,
            pinnedFocusApps = uiState.pinnedFocusApps,
            discoveredApps = uiState.discoveredApps,
            onDismiss = { viewModel.setShowSelectFocusAppSheet(false) },
            onSelectFocusApp = viewModel::selectPinnedFocusApp,
            onAddCustomFocusApp = viewModel::addAndSelectCustomFocusApp
        )

        SelectFocusExceptionsBottomSheet(
            isVisible = uiState.showSelectFocusExceptionsSheet,
            exceptionApps = uiState.focusExceptionApps,
            discoveredApps = uiState.discoveredApps,
            pinnedFocusPackageName = uiState.selectedFocusApp?.packageName
                ?: uiState.activeSession.pinnedPackageName,
            onDismiss = { viewModel.setShowSelectFocusExceptionsSheet(false) },
            onToggleExceptionApp = viewModel::toggleFocusExceptionApp
        )

        SystemPermissionsBottomSheet(
            isVisible = uiState.showPermissionsSheet,
            hasUsageAccess = uiState.hasUsageStatsPermission,
            hasAccessibility = uiState.hasAccessibilityPermission,
            hasOverlay = uiState.hasOverlayPermission,
            hasNotifications = uiState.hasNotificationPermission,
            isBatteryOptimizedExempt = uiState.isIgnoringBatteryOptimizations,
            isXiaomi = uiState.isXiaomiDevice,
            onDismiss = { viewModel.setShowPermissionsSheet(false) },
            onOpenUsageAccess = onLaunchUsageSettings,
            onOpenAccessibility = onLaunchAccessibilitySettings,
            onOpenOverlay = onLaunchOverlaySettings,
            onOpenAppDetailsSettings = onLaunchAppDetailsSettings,
            onOpenNotifications = onLaunchNotificationSettings,
            onOpenBatteryOptimization = onLaunchBatteryOptimization,
            onOpenXiaomiAutostart = if (uiState.isXiaomiDevice) onLaunchXiaomiAutostart else null,
            onCheckForUpdates = viewModel::checkForUpdates
        )

        ExpressiveUpdateModalSheet(
            updateState = updateState,
            onDismiss = viewModel::dismissUpdateSheet,
            onDownloadClick = viewModel::startUpdateDownload,
            onInstallClick = viewModel::installUpdateApk,
            onOpenUnknownSourcesSettings = {
                try {
                    context.startActivity(viewModel.buildUnknownAppSourcesSettingsIntent())
                } catch (_: Exception) {
                }
            },
            onRetryCheck = viewModel::checkForUpdates
        )

        // Full-Screen Hard Puzzle Gate
        uiState.activeChallenge?.let { challenge ->
            FrictionShieldOverlay(
                challenge = challenge,
                onSwitchMode = viewModel::switchChallengeMode,
                onTapMemoryTile = viewModel::onTapMemoryTile,
                onReplayMemoryPattern = viewModel::replayMemoryPattern,
                onPhraseChanged = viewModel::updateChallengeTypedPhrase,
                onConfirmPhraseUnlock = viewModel::confirmPhraseChallengeUnlock,
                onMathInputChanged = viewModel::updateChallengeMathInput,
                onSubmitMathStep = viewModel::submitMathStepOrUnlock,
                onKeepLocked = { viewModel.dismissFrictionChallengeKeepLocked(context) }
            )
        }
    }
}

@Composable
internal fun ExpressiveTopBar(
    currentTab: AppTab,
    allPermissionsGranted: Boolean = true,
    isUpdateAvailable: Boolean = false,
    useDynamicWallpaper: Boolean,
    isTablet: Boolean,
    onOpenPermissionsSheet: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onToggleWallpaperTheme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isTablet) Modifier.statusBarsPadding() else Modifier)
            .padding(
                horizontal = if (isTablet) 24.dp else 20.dp,
                vertical = if (isTablet) 14.dp else 12.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentTab == AppTab.SETTINGS && onNavigateBack != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .expressiveClickable { onNavigateBack() }
                        .testTag("top_bar_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    LockInMotion.directionalTabTransition(
                        movingForward = targetState.ordinal >= initialState.ordinal
                    )
                },
                label = "top_bar_title_transition"
            ) { tab ->
                Text(
                    text = when (tab) {
                        AppTab.APP_LIMITS -> "App Limits"
                        AppTab.FOCUS_LOCK -> "Focus Lock"
                        AppTab.SETTINGS -> "Settings"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. In-App Update Button: ONLY visible when an update is actually available!
            if (isUpdateAvailable) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .expressiveClickable { onCheckForUpdates() }
                        .padding(horizontal = 14.dp)
                        .testTag("check_updates_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = "Update Available",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Update",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 2. Shield Setup Button: ONLY visible when essential permissions are missing!
            if (!allPermissionsGranted) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .expressiveClickable { onOpenPermissionsSheet() }
                        .testTag("open_permissions_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Protection Setup Required",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 3. Dynamic / Wallpaper Palette Toggle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ExpressiveSealShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .expressiveClickable { onToggleWallpaperTheme() }
                    .testTag("toggle_palette_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "Switch Expressive / Wallpaper Palette",
                    tint = if (useDynamicWallpaper) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. Dedicated Settings Button (Hidden when already on Settings)
            if (currentTab != AppTab.SETTINGS) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .expressiveClickable { onOpenSettings() }
                        .testTag("open_settings_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingExpressivePillDock(
    currentTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier.height(68.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveDockPillItem(
                label = "App Limits",
                selected = currentTab == AppTab.APP_LIMITS,
                selectedIcon = Icons.Filled.HourglassBottom,
                unselectedIcon = Icons.Outlined.HourglassEmpty,
                onClick = { onSelectTab(AppTab.APP_LIMITS) },
                modifier = Modifier.testTag("nav_tab_app_vault")
            )

            ExpressiveDockPillItem(
                label = "Focus Lock",
                selected = currentTab == AppTab.FOCUS_LOCK,
                selectedIcon = Icons.Filled.Lock,
                unselectedIcon = Icons.Outlined.Lock,
                onClick = { onSelectTab(AppTab.FOCUS_LOCK) },
                modifier = Modifier.testTag("nav_tab_read_lock")
            )
        }
    }
}

@Composable
private fun ExpressiveDockPillItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalPad by animateDpAsState(
        targetValue = if (selected) 24.dp else 18.dp,
        animationSpec = LockInMotion.subtleBounceSpring(),
        label = "DockPillPadding"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = LockInMotion.spatialSpring(),
        label = "DockPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = LockInMotion.spatialSpring(),
        label = "DockPillContent"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(bgColor)
            .expressiveClickable(onClick = onClick)
            .padding(horizontal = horizontalPad),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
internal fun ExpressiveNavigationRail(
    currentTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onQuickAddLimit: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
        modifier = Modifier
            .width(104.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Brand Emblem
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(ExpressiveCloverShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "LockIN",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "LockIN",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Center Navigation Items
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExpressiveRailItem(
                    label = "Limits",
                    selected = currentTab == AppTab.APP_LIMITS,
                    selectedIcon = Icons.Filled.HourglassBottom,
                    unselectedIcon = Icons.Outlined.HourglassEmpty,
                    onClick = { onSelectTab(AppTab.APP_LIMITS) },
                    modifier = Modifier.testTag("nav_tab_app_vault")
                )

                ExpressiveRailItem(
                    label = "Focus",
                    selected = currentTab == AppTab.FOCUS_LOCK,
                    selectedIcon = Icons.Filled.Lock,
                    unselectedIcon = Icons.Outlined.Lock,
                    onClick = { onSelectTab(AppTab.FOCUS_LOCK) },
                    modifier = Modifier.testTag("nav_tab_read_lock")
                )

                ExpressiveRailItem(
                    label = "Settings",
                    selected = currentTab == AppTab.SETTINGS,
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    onClick = { onSelectTab(AppTab.SETTINGS) },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }

            // Functional Quick Action Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .expressiveClickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQuickAddLimit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New App Limit",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpressiveRailItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = LockInMotion.spatialSpring(),
        label = "RailItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = LockInMotion.spatialSpring(),
        label = "RailItemContent"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .expressiveClickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(40.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
