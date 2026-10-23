package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppTimerRuleEntity
import com.example.data.DiscoveredDeviceApp
import com.example.data.FocusExceptionAppEntity
import com.example.data.PinnedFocusAppEntity
import com.example.service.BootReceiver
import com.example.service.ChapterLockEnforcer
import com.example.service.InterceptReason
import com.example.ui.components.FrictionShieldOverlay
import com.example.ui.components.SelectFocusAppBottomSheet
import com.example.ui.components.SelectFocusAppSheetContent
import com.example.ui.components.SystemPermissionsBottomSheet
import com.example.ui.components.SystemPermissionsSheetContent
import com.example.ui.screens.AppVaultScreen
import com.example.ui.screens.ExpressiveSettingsScreen
import com.example.ui.screens.ReadAndLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.updater.UpdateState
import com.example.viewmodel.ActiveFocusSessionState
import com.example.viewmodel.AppTab
import com.example.viewmodel.ChallengeMode
import com.example.viewmodel.FrictionChallengeState
import com.example.viewmodel.FrictionReason
import com.example.viewmodel.MathStepProblem
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        ChapterLockEnforcer.updateFocusLockState(
            active = false,
            pinnedPackage = null,
            pinnedName = "",
            endTimeMs = 0L
        )
        ChapterLockEnforcer.syncRules(emptyList())
        ChapterLockEnforcer.syncFocusExceptions(emptySet())
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LockIN", appName)
    }

    @Test
    fun `app timer rule blocks when daily limit is reached or focus session is active`() {
        val ruleUnderBudget = AppTimerRuleEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            category = "Social",
            dailyLimitMinutes = 60,
            usedMinutesToday = 45,
            blockDuringFocus = true
        )
        assertFalse(ruleUnderBudget.isCurrentlyBlocked(isFocusSessionActive = false))
        assertTrue(ruleUnderBudget.isCurrentlyBlocked(isFocusSessionActive = true))

        val ruleOverBudget = ruleUnderBudget.copy(usedMinutesToday = 60)
        assertTrue(ruleOverBudget.isCurrentlyBlocked(isFocusSessionActive = false))
    }

    @Test
    fun `ChapterLockEnforcer evaluates packages accurately without disk or DB overhead`() {
        val youtubeRule = AppTimerRuleEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            category = "Video",
            dailyLimitMinutes = 30,
            usedMinutesToday = 35,
            blockDuringFocus = true
        )
        ChapterLockEnforcer.syncRules(listOf(youtubeRule))
        ChapterLockEnforcer.updateFocusLockState(
            active = false,
            pinnedPackage = null,
            pinnedName = "",
            endTimeMs = 0L
        )

        val launcherDecision = ChapterLockEnforcer.evaluatePackage("com.miui.home", "com.example")
        assertNull("System launcher must never be intercepted", launcherDecision)

        val systemUiDecision = ChapterLockEnforcer.evaluatePackage("com.android.systemui", "com.example")
        assertNull("SystemUI must never be intercepted", systemUiDecision)

        val ytDecision = ChapterLockEnforcer.evaluatePackage("com.google.android.youtube", "com.example")
        assertNotNull(ytDecision)
        assertEquals(InterceptReason.DAILY_LIMIT_EXHAUSTED, ytDecision?.reason)
    }

    @Test
    fun `temporary unlock pass bypasses block until expiration`() {
        val rule = AppTimerRuleEntity(
            packageName = "com.twitter.android",
            appName = "X",
            category = "Social",
            dailyLimitMinutes = 30,
            usedMinutesToday = 35
        )
        ChapterLockEnforcer.syncRules(listOf(rule))
        ChapterLockEnforcer.grantTemporaryUnlock("com.twitter.android", durationMinutes = 10)

        assertTrue(ChapterLockEnforcer.isPackageTemporarilyUnlocked("com.twitter.android"))
        val decision = ChapterLockEnforcer.evaluatePackage("com.twitter.android", "com.example")
        assertNull("Temporarily unlocked app must not be intercepted", decision)
    }

    @Test
    fun `focus lock mode intercepts unauthorized apps while keeping pinned focus app open`() {
        val pinnedAppPkg = "com.google.android.youtube"
        val now = System.currentTimeMillis()
        ChapterLockEnforcer.updateFocusLockState(
            active = true,
            pinnedPackage = pinnedAppPkg,
            pinnedName = "YouTube Lectures",
            endTimeMs = now + 1_800_000L
        )

        val pinnedDecision = ChapterLockEnforcer.evaluatePackage(pinnedAppPkg, "com.example")
        assertNull("Pinned focus app must be permitted during focus lock", pinnedDecision)

        val unauthorizedDecision = ChapterLockEnforcer.evaluatePackage("com.instagram.android", "com.example")
        assertNotNull(unauthorizedDecision)
        assertEquals(InterceptReason.FOCUS_LOCK_BREACH, unauthorizedDecision?.reason)

        // System launcher must trigger FOCUS_LOCK_LAUNCHER_BREACH when Focus Lock is active to confine navigation
        val launcherDecision = ChapterLockEnforcer.evaluatePackage("com.miui.home", "com.example")
        assertNotNull(launcherDecision)
        assertEquals(InterceptReason.FOCUS_LOCK_LAUNCHER_BREACH, launcherDecision?.reason)

        // Built-in emergency dialer must always be permitted during Focus Lock
        val dialerDecision = ChapterLockEnforcer.evaluatePackage("com.google.android.dialer", "com.example")
        assertNull("Emergency / Phone dialer must always be permitted during Focus Lock", dialerDecision)

        // User-added exception apps (e.g. GPay) must be permitted during Focus Lock
        val gpayPkg = "com.google.android.apps.nbu.paisa.user"
        ChapterLockEnforcer.syncFocusExceptions(setOf(gpayPkg))
        val gpayDecision = ChapterLockEnforcer.evaluatePackage(gpayPkg, "com.example")
        assertNull("User-configured exception app (GPay) must be permitted during Focus Lock", gpayDecision)
        ChapterLockEnforcer.syncFocusExceptions(emptySet())
        ChapterLockEnforcer.updateFocusLockState(
            active = false,
            pinnedPackage = null,
            pinnedName = "",
            endTimeMs = 0L
        )
    }

    @Test
    fun `permissions card completely disappears from home screen once all 3 permissions are granted`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                AppVaultScreen(
                    appRules = emptyList(),
                    isFocusSessionActive = false,
                    hasUsageStatsPermission = true,
                    hasAccessibilityPermission = true,
                    hasOverlayPermission = true,
                    isTablet = false,
                    onOpenUsageAccessSettings = {},
                    onOpenAccessibilitySettings = {},
                    onOpenOverlaySettings = {},
                    onOpenPermissionsSheet = {},
                    onTapAppRule = {},
                    onRequestUnlockApp = {},
                    onOpenAddAppSheet = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("missing_permissions_card").assertDoesNotExist()
    }

    @Test
    fun `only ungranted permissions are shown when permissions are partially granted`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 960.dp, height = 640.dp)) {
                    AppVaultScreen(
                        appRules = emptyList(),
                        isFocusSessionActive = false,
                        hasUsageStatsPermission = true,
                        hasAccessibilityPermission = true,
                        hasOverlayPermission = false,
                        isTablet = true,
                        onOpenUsageAccessSettings = {},
                        onOpenAccessibilitySettings = {},
                        onOpenOverlaySettings = {},
                        onOpenPermissionsSheet = {},
                        onTapAppRule = {},
                        onRequestUnlockApp = {},
                        onOpenAddAppSheet = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("missing_permissions_card").assertExists()
        composeTestRule.onNodeWithText("Display Over Other Apps").assertExists()
        composeTestRule.onNodeWithText("Usage Access").assertDoesNotExist()
        composeTestRule.onNodeWithText("Accessibility Blocker").assertDoesNotExist()
    }

    @Test
    fun `BootReceiver handles broadcast without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun `LockNotificationManager builds live chronometer notification during Focus Lock and summary when idle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChapterLockEnforcer.updateFocusLockState(
            active = true,
            pinnedPackage = "com.google.android.youtube",
            pinnedName = "YouTube Lectures",
            endTimeMs = System.currentTimeMillis() + 30 * 60_000L
        )
        val focusNotif = com.example.service.LockNotificationManager.buildForegroundServiceNotification(context)
        assertNotNull(focusNotif)
        assertEquals(3, focusNotif.actions?.size ?: 0)

        ChapterLockEnforcer.updateFocusLockState(
            active = false,
            pinnedPackage = null,
            pinnedName = "",
            endTimeMs = 0L
        )
        val idleNotif = com.example.service.LockNotificationManager.buildForegroundServiceNotification(context)
        assertNotNull(idleNotif)
        assertEquals(2, idleNotif.actions?.size ?: 0)
    }

    @Test
    fun `connected focus session banner appears on AppVaultScreen when Focus Lock is active`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                AppVaultScreen(
                    appRules = emptyList(),
                    isFocusSessionActive = true,
                    activeFocusAppName = "YouTube Lectures",
                    activeFocusPackageName = "com.google.android.youtube",
                    activeFocusCountdown = "42:15",
                    hasUsageStatsPermission = true,
                    hasAccessibilityPermission = true,
                    hasOverlayPermission = true,
                    isTablet = false,
                    onOpenUsageAccessSettings = {},
                    onOpenAccessibilitySettings = {},
                    onOpenOverlaySettings = {},
                    onOpenPermissionsSheet = {},
                    onTapAppRule = {},
                    onRequestUnlockApp = {},
                    onOpenAddAppSheet = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connected_focus_session_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("42:15").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of AppLimits screen`() {
        composeTestRule.mainClock.autoAdvance = false
        val sampleRules = listOf(
            AppTimerRuleEntity(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                category = "Video",
                dailyLimitMinutes = 45,
                usedMinutesToday = 45
            ),
            AppTimerRuleEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                category = "Social",
                dailyLimitMinutes = 60,
                usedMinutesToday = 32
            ),
            AppTimerRuleEntity(
                packageName = "com.zhiliaoapp.musically",
                appName = "TikTok",
                category = "Short Video",
                dailyLimitMinutes = 30,
                usedMinutesToday = 12
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 840.dp)) {
                    AppVaultScreen(
                        appRules = sampleRules,
                        isFocusSessionActive = false,
                        hasUsageStatsPermission = true,
                        hasAccessibilityPermission = true,
                        hasOverlayPermission = true,
                        isTablet = false,
                        onOpenUsageAccessSettings = {},
                        onOpenAccessibilitySettings = {},
                        onOpenPermissionsSheet = {},
                        onTapAppRule = {},
                        onRequestUnlockApp = {},
                        onOpenAddAppSheet = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("app_limits_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of FocusLock screen`() {
        composeTestRule.mainClock.autoAdvance = false
        val pinnedFocusApp = PinnedFocusAppEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube Lectures",
            subtitle = "Study & Focus App",
            isSelected = true,
            totalFocusedMinutes = 185,
            isInstalled = true
        )
        val activeSession = ActiveFocusSessionState(
            isActive = true,
            pinnedAppName = "YouTube Lectures",
            pinnedPackageName = "com.google.android.youtube",
            selectedDurationMinutes = 45,
            totalSeconds = 2700,
            remainingSeconds = 2142
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 840.dp)) {
                    ReadAndLockScreen(
                        selectedFocusApp = pinnedFocusApp,
                        activeSession = activeSession,
                        isTablet = false,
                        onSelectDuration = {},
                        onStartFocusLock = {},
                        onLaunchPinnedApp = {},
                        onRequestQuitFocusEarly = {},
                        onOpenSelectFocusAppSheet = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("focus_lock_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of FrictionShieldOverlay screen`() {
        composeTestRule.mainClock.autoAdvance = false
        val challenge = FrictionChallengeState(
            reason = FrictionReason.QUIT_FOCUS_LOCK_EARLY,
            targetAppName = "YouTube Lectures",
            targetPackageName = "com.google.android.youtube",
            mode = ChallengeMode.MEMORY_GRID,
            patternSequence = listOf(2, 5, 10, 13, 7),
            userClickedSequence = listOf(2, 5),
            currentlyFlashingTile = 10,
            isRevealingPattern = false,
            gridStatusMessage = "Tap 5 tiles in order (2/5)",
            mathSteps = listOf(MathStepProblem(1, 3, "(18 × 6) + 27", 135))
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 840.dp)) {
                    FrictionShieldOverlay(
                        challenge = challenge,
                        onSwitchMode = {},
                        onTapMemoryTile = {},
                        onReplayMemoryPattern = {},
                        onPhraseChanged = {},
                        onConfirmPhraseUnlock = {},
                        onMathInputChanged = {},
                        onSubmitMathStep = {},
                        onKeepLocked = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("friction_puzzle_phone.png")
    }

    @Test
    @Config(qualifiers = "w960dp-h640dp-xhdpi")
    fun `capture real screenshot of Tablet 3 to 2 layout`() {
        composeTestRule.mainClock.autoAdvance = false
        val pinnedFocusApp = PinnedFocusAppEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube Lectures",
            subtitle = "Study & Focus App",
            isSelected = true,
            totalFocusedMinutes = 240,
            isInstalled = true
        )
        val session = ActiveFocusSessionState(
            isActive = false,
            pinnedAppName = "YouTube Lectures",
            pinnedPackageName = "com.google.android.youtube",
            selectedDurationMinutes = 60,
            totalSeconds = 3600,
            remainingSeconds = 3600
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 960.dp, height = 640.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.size(width = 960.dp, height = 640.dp)
                    ) {
                        ExpressiveNavigationRail(
                            currentTab = AppTab.FOCUS_LOCK,
                            onSelectTab = {}
                        )
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            ExpressiveTopBar(
                                currentTab = AppTab.FOCUS_LOCK,
                                allPermissionsGranted = true,
                                useDynamicWallpaper = false,
                                isTablet = true,
                                onToggleWallpaperTheme = {}
                            )
                            ReadAndLockScreen(
                                selectedFocusApp = pinnedFocusApp,
                                activeSession = session,
                                focusHistoryCount = 6,
                                isTablet = true,
                                onSelectDuration = {},
                                onStartFocusLock = {},
                                onLaunchPinnedApp = {},
                                onRequestQuitFocusEarly = {},
                                onOpenSelectFocusAppSheet = {}
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("tablet_3_2_layout.png")
    }

    @Test
    @Config(qualifiers = "w960dp-h640dp-xhdpi")
    fun `capture real screenshot of Tablet 3 to 2 AppLimits layout`() {
        composeTestRule.mainClock.autoAdvance = false
        val rules = listOf(
            AppTimerRuleEntity(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                category = "Video",
                dailyLimitMinutes = 45,
                usedMinutesToday = 45
            ),
            AppTimerRuleEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                category = "Social",
                dailyLimitMinutes = 60,
                usedMinutesToday = 32
            ),
            AppTimerRuleEntity(
                packageName = "com.zhiliaoapp.musically",
                appName = "TikTok",
                category = "Short Video",
                dailyLimitMinutes = 30,
                usedMinutesToday = 12
            )
        )
        val discovered = listOf(
            DiscoveredDeviceApp(
                packageName = "com.reddit.frontpage",
                appName = "Reddit",
                category = "Forums",
                usedMinutesToday = 24
            ),
            DiscoveredDeviceApp(
                packageName = "com.twitter.android",
                appName = "X",
                category = "Social",
                usedMinutesToday = 18
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 960.dp, height = 640.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.size(width = 960.dp, height = 640.dp)
                    ) {
                        ExpressiveNavigationRail(
                            currentTab = AppTab.APP_LIMITS,
                            onSelectTab = {}
                        )
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            ExpressiveTopBar(
                                currentTab = AppTab.APP_LIMITS,
                                allPermissionsGranted = true,
                                useDynamicWallpaper = false,
                                isTablet = true,
                                onToggleWallpaperTheme = {}
                            )
                            AppVaultScreen(
                                appRules = rules,
                                discoveredApps = discovered,
                                isFocusSessionActive = false,
                                hasUsageStatsPermission = true,
                                hasAccessibilityPermission = true,
                                hasOverlayPermission = true,
                                isTablet = true,
                                onOpenUsageAccessSettings = {},
                                onOpenAccessibilitySettings = {},
                                onOpenOverlaySettings = {},
                                onOpenPermissionsSheet = {},
                                onTapAppRule = {},
                                onRequestUnlockApp = {},
                                onOpenAddAppSheet = {}
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("tablet_app_limits_layout.png")
    }

    @Test
    @Config(qualifiers = "w768dp-h1024dp-xhdpi")
    fun `portrait tablet uses centered single-column layout and slot-machine clock displays real-time seconds`() {
        composeTestRule.mainClock.autoAdvance = false
        val pinnedFocusApp = PinnedFocusAppEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube Lectures",
            subtitle = "Study & Focus App",
            isSelected = true,
            totalFocusedMinutes = 185,
            isInstalled = true
        )
        val idleSession = ActiveFocusSessionState(
            isActive = false,
            pinnedAppName = "YouTube Lectures",
            pinnedPackageName = "com.google.android.youtube",
            selectedDurationMinutes = 90,
            totalSeconds = 5400,
            remainingSeconds = 5400
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 768.dp, height = 1024.dp)) {
                    ReadAndLockScreen(
                        selectedFocusApp = pinnedFocusApp,
                        activeSession = idleSession,
                        focusHistoryCount = 4,
                        isTablet = false,
                        onSelectDuration = {},
                        onStartFocusLock = {},
                        onLaunchPinnedApp = {},
                        onRequestQuitFocusEarly = {},
                        onOpenSelectFocusAppSheet = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.onNodeWithTag("slot_machine_clock_display").assertIsDisplayed()
        saveScreenshot("portrait_tablet_focus_lock.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of Friction Math Challenge mode`() {
        composeTestRule.mainClock.autoAdvance = false
        val mathChallenge = FrictionChallengeState(
            reason = FrictionReason.QUIT_FOCUS_LOCK_EARLY,
            targetAppName = "YouTube Lectures",
            targetPackageName = "com.google.android.youtube",
            mode = ChallengeMode.MATH_CHAIN,
            patternSequence = listOf(2, 5, 10, 13, 7),
            userClickedSequence = listOf(2, 5),
            mathSteps = listOf(
                MathStepProblem(1, 3, "(18 × 6) + 27", 135),
                MathStepProblem(2, 3, "(112 − 44) × 3", 204),
                MathStepProblem(3, 3, "(24 × 7) − 39", 129)
            ),
            currentMathStepIndex = 0,
            mathInput = "13",
            mathErrorMessage = null
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 840.dp)) {
                    FrictionShieldOverlay(
                        challenge = mathChallenge,
                        onSwitchMode = {},
                        onTapMemoryTile = {},
                        onReplayMemoryPattern = {},
                        onPhraseChanged = {},
                        onConfirmPhraseUnlock = {},
                        onMathInputChanged = {},
                        onSubmitMathStep = {},
                        onKeepLocked = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("friction_math_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of System Permissions sheet`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .size(width = 411.dp, height = 840.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SystemPermissionsSheetContent(
                            hasUsageAccess = true,
                            hasAccessibility = false,
                            hasOverlay = true,
                            hasNotifications = true,
                            isBatteryOptimizedExempt = false,
                            isXiaomi = true,
                            onDismiss = {},
                            onOpenUsageAccess = {},
                            onOpenAccessibility = {},
                            onOpenOverlay = {},
                            onOpenAppDetailsSettings = {},
                            onOpenNotifications = {},
                            onOpenBatteryOptimization = {},
                            onOpenXiaomiAutostart = {}
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("permissions_sheet_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of Select Focus App sheet`() {
        composeTestRule.mainClock.autoAdvance = false
        val pinned = listOf(
            PinnedFocusAppEntity("com.google.android.youtube", "YouTube Lectures", "Video & Lectures", true, 185, true),
            PinnedFocusAppEntity("com.duolingo", "Duolingo", "Education & Learning", false, 60, true),
            PinnedFocusAppEntity("com.termux", "Termux", "Developer & Tools", false, 120, true),
            PinnedFocusAppEntity("md.obsidian", "Obsidian", "Productivity & Notes", false, 90, true)
        )
        val discovered = listOf(
            DiscoveredDeviceApp("org.coursera.android", "Coursera", "Education & Learning", true, true, 45),
            DiscoveredDeviceApp("com.anki", "AnkiDroid", "Education & Learning", true, true, 20),
            DiscoveredDeviceApp("com.google.android.apps.docs.editors.docs", "Google Docs", "Productivity & Tools", true, true, 35)
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .size(width = 411.dp, height = 840.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectFocusAppSheetContent(
                            pinnedFocusApps = pinned,
                            discoveredApps = discovered,
                            onDismiss = {},
                            onSelectFocusApp = {},
                            onAddCustomFocusApp = { _, _, _ -> }
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("select_focus_app_sheet.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun `capture real screenshot of Phone in Landscape Focus Lock`() {
        composeTestRule.mainClock.autoAdvance = false
        val pinnedFocusApp = PinnedFocusAppEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube Lectures",
            subtitle = "Study & Focus App",
            isSelected = true,
            totalFocusedMinutes = 185,
            isInstalled = true
        )
        val activeSession = ActiveFocusSessionState(
            isActive = true,
            pinnedAppName = "YouTube Lectures",
            pinnedPackageName = "com.google.android.youtube",
            selectedDurationMinutes = 45,
            totalSeconds = 2700,
            remainingSeconds = 2142
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 891.dp, height = 411.dp)) {
                    ReadAndLockScreen(
                        selectedFocusApp = pinnedFocusApp,
                        activeSession = activeSession,
                        isTablet = false,
                        onSelectDuration = {},
                        onStartFocusLock = {},
                        onLaunchPinnedApp = {},
                        onRequestQuitFocusEarly = {},
                        onOpenSelectFocusAppSheet = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("phone_landscape_focus_lock.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun `capture real screenshot of Phone in Landscape App Limits`() {
        composeTestRule.mainClock.autoAdvance = false
        val sampleRules = listOf(
            AppTimerRuleEntity(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                category = "Video",
                dailyLimitMinutes = 45,
                usedMinutesToday = 45
            ),
            AppTimerRuleEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                category = "Social",
                dailyLimitMinutes = 60,
                usedMinutesToday = 32
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 891.dp, height = 411.dp)) {
                    AppVaultScreen(
                        appRules = sampleRules,
                        isFocusSessionActive = false,
                        hasUsageStatsPermission = true,
                        hasAccessibilityPermission = true,
                        hasOverlayPermission = true,
                        isTablet = false,
                        onOpenUsageAccessSettings = {},
                        onOpenAccessibilitySettings = {},
                        onOpenPermissionsSheet = {},
                        onTapAppRule = {},
                        onRequestUnlockApp = {},
                        onOpenAddAppSheet = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("phone_landscape_app_limits.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of Settings screen`() {
        composeTestRule.mainClock.autoAdvance = false
        val exceptions = listOf(
            FocusExceptionAppEntity("com.google.android.dialer", "Phone & Emergency", "Emergency"),
            FocusExceptionAppEntity("com.google.android.apps.nbu.paisa.user", "Google Pay", "Payments")
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 840.dp)) {
                    ExpressiveSettingsScreen(
                        hasUsageStatsPermission = true,
                        hasAccessibilityPermission = true,
                        hasOverlayPermission = true,
                        hasNotificationPermission = true,
                        isBatteryOptimizedExempt = true,
                        isXiaomiDevice = false,
                        focusExceptionApps = exceptions,
                        selectedDurationMinutes = 45,
                        updateState = UpdateState.Idle,
                        isTablet = false,
                        onNavigateBackToHome = {},
                        onOpenUsageAccessSettings = {},
                        onOpenAccessibilitySettings = {},
                        onOpenOverlaySettings = {},
                        onOpenAppDetailsSettings = {},
                        onOpenNotificationSettings = {},
                        onOpenBatteryOptimizationSettings = {},
                        onOpenXiaomiAutostartSettings = null,
                        onOpenExceptionsSheet = {},
                        onSelectDurationMinutes = {},
                        onCheckForUpdates = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("settings_screen_phone.png")
    }

    private fun saveScreenshot(fileName: String) {
        val node = composeTestRule.onRoot().fetchSemanticsNode()
        val view = (node.root as ViewRootForTest).view
        val width = view.width.coerceAtLeast(400)
        val height = view.height.coerceAtLeast(400)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#120F17"))
        view.draw(canvas)

        val projectRoot = File(System.getProperty("user.dir") ?: ".").let { dir ->
            if (dir.name == "app") dir.parentFile ?: dir else dir
        }
        val outDir = File(projectRoot, "docs/screenshots")
        outDir.mkdirs()
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Screenshot $fileName must exist and be non-empty", outFile.exists() && outFile.length() > 1000)
    }
}

