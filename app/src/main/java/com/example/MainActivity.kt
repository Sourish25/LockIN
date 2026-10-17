package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.example.ui.components.AddAppTimerBottomSheet
import com.example.ui.components.EditAppLimitBottomSheet
import com.example.ui.components.ExpressiveSealShape
import com.example.ui.components.FrictionShieldOverlay
import com.example.ui.components.SelectFocusAppBottomSheet
import com.example.ui.components.SystemPermissionsBottomSheet
import com.example.ui.screens.AppVaultScreen
import com.example.ui.screens.ReadAndLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.ChapterLockUiState
import com.example.viewmodel.ChapterLockViewModel
import com.example.viewmodel.FrictionReason

class MainActivity : ComponentActivity() {

    private var viewModelInstance: ChapterLockViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Always ensure the background guardian service is running
        AppMonitorService.start(applicationContext)

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

            // Handle intent on initial launch
            LaunchedEffect(intent) {
                checkAndHandleIntent(intent, viewModel)
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshPermissionsAndApps()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelInstance?.let { checkAndHandleIntent(intent, it) }
    }

    private fun checkAndHandleIntent(intent: Intent?, vm: ChapterLockViewModel) {
        if (intent == null) return
        val interceptedPkg = intent.getStringExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE)
        val interceptReason = intent.getStringExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON)
        if (!interceptedPkg.isNullOrBlank()) {
            vm.handleInterception(interceptedPkg, interceptReason ?: "INTERCEPTED")
            // Clear extras so rotation doesn't re-trigger
            intent.removeExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE)
            intent.removeExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON)
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
    onLaunchBatteryOptimization: () -> Unit,
    onLaunchXiaomiAutostart: () -> Unit,
    onLaunchPinnedApp: (String) -> Unit
) {
    val context = LocalContext.current

    if (uiState.currentTab != AppTab.APP_LIMITS && uiState.activeChallenge == null) {
        BackHandler {
            viewModel.selectTab(AppTab.APP_LIMITS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 700.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ExpressiveTopBar(
                        currentTab = uiState.currentTab,
                        activeFocusRunning = uiState.activeSession.isActive,
                        useDynamicWallpaper = useDynamicWallpaper,
                        onToggleWallpaperTheme = onToggleWallpaperTheme
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isTablet) {
                            CleanNavigationRail(
                                currentTab = uiState.currentTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        ) {
                            when (uiState.currentTab) {
                                AppTab.APP_LIMITS -> {
                                    AppVaultScreen(
                                        appRules = uiState.appRules,
                                        isFocusSessionActive = uiState.activeSession.isActive,
                                        hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                                        hasAccessibilityPermission = uiState.hasAccessibilityPermission,
                                        isIgnoringBattery = uiState.isIgnoringBatteryOptimizations,
                                        isXiaomi = uiState.isXiaomiDevice,
                                        isTablet = isTablet,
                                        onOpenUsageAccessSettings = onLaunchUsageSettings,
                                        onOpenAccessibilitySettings = onLaunchAccessibilitySettings,
                                        onOpenPermissionsSheet = onOpenPermissionsSheet,
                                        onTapAppRule = viewModel::onTapAppRuleCard,
                                        onRequestUnlockApp = { rule ->
                                            viewModel.openFrictionChallenge(
                                                reason = FrictionReason.UNLOCK_BLOCKED_APP,
                                                targetAppName = rule.appName,
                                                targetPackageName = rule.packageName
                                            )
                                        },
                                        onOpenAddAppSheet = { viewModel.setShowAddAppSheet(true) }
                                    )
                                }

                                AppTab.FOCUS_LOCK -> {
                                    ReadAndLockScreen(
                                        selectedFocusApp = uiState.selectedFocusApp,
                                        activeSession = uiState.activeSession,
                                        isTablet = isTablet,
                                        onSelectDuration = viewModel::selectFocusDuration,
                                        onStartFocusLock = { viewModel.startFocusLock(context) },
                                        onLaunchPinnedApp = onLaunchPinnedApp,
                                        onRequestQuitFocusEarly = viewModel::requestQuitFocusLockEarly,
                                        onOpenSelectFocusAppSheet = {
                                            viewModel.setShowSelectFocusAppSheet(true)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Floating Pill Dock on Phone
                    if (!isTablet) {
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

        SystemPermissionsBottomSheet(
            isVisible = uiState.showPermissionsSheet,
            hasUsageAccess = uiState.hasUsageStatsPermission,
            hasAccessibility = uiState.hasAccessibilityPermission,
            hasOverlay = uiState.hasOverlayPermission,
            isBatteryOptimizedExempt = uiState.isIgnoringBatteryOptimizations,
            isXiaomi = uiState.isXiaomiDevice,
            onDismiss = { viewModel.setShowPermissionsSheet(false) },
            onOpenUsageAccess = onLaunchUsageSettings,
            onOpenAccessibility = onLaunchAccessibilitySettings,
            onOpenOverlay = onLaunchOverlaySettings,
            onOpenBatteryOptimization = onLaunchBatteryOptimization,
            onOpenXiaomiAutostart = if (uiState.isXiaomiDevice) onLaunchXiaomiAutostart else null
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
                onKeepLocked = viewModel::dismissFrictionChallengeKeepLocked
            )
        }
    }
}

@Composable
private fun ExpressiveTopBar(
    currentTab: AppTab,
    activeFocusRunning: Boolean,
    useDynamicWallpaper: Boolean,
    onToggleWallpaperTheme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (currentTab) {
                AppTab.APP_LIMITS -> "App Limits"
                AppTab.FOCUS_LOCK -> "Focus Lock"
            },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (activeFocusRunning) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Focus Active",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ExpressiveSealShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onToggleWallpaperTheme() }
                    .testTag("toggle_palette_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "Switch Expressive / Wallpaper Palette",
                    tint = if (useDynamicWallpaper) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
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
    val haptics = LocalHapticFeedback.current

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
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(AppTab.APP_LIMITS)
                },
                modifier = Modifier.testTag("nav_tab_app_vault")
            )

            ExpressiveDockPillItem(
                label = "Focus Lock",
                selected = currentTab == AppTab.FOCUS_LOCK,
                selectedIcon = Icons.Filled.Lock,
                unselectedIcon = Icons.Outlined.Lock,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(AppTab.FOCUS_LOCK)
                },
                modifier = Modifier.testTag("nav_tab_read_lock")
            )
        }
    }
}

@Composable
private fun ExpressiveDockPillItem(
    label: String,
    selected: Boolean,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalPad by animateDpAsState(
        targetValue = if (selected) 24.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "DockPillPadding"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "DockPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "DockPillContent"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
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
private fun CleanNavigationRail(
    currentTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        NavigationRailItem(
            selected = currentTab == AppTab.APP_LIMITS,
            onClick = { onSelectTab(AppTab.APP_LIMITS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.APP_LIMITS) Icons.Filled.HourglassBottom
                    else Icons.Outlined.HourglassEmpty,
                    contentDescription = "App Limits"
                )
            },
            label = { Text("App Limits") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = currentTab == AppTab.FOCUS_LOCK,
            onClick = { onSelectTab(AppTab.FOCUS_LOCK) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.FOCUS_LOCK) Icons.Filled.Lock
                    else Icons.Outlined.Lock,
                    contentDescription = "Focus Lock"
                )
            },
            label = { Text("Focus Lock") }
        )
    }
}
