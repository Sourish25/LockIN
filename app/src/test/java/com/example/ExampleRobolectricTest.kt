package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppTimerRuleEntity
import com.example.data.PinnedFocusAppEntity
import com.example.data.SystemAppInspector
import com.example.service.BootReceiver
import com.example.service.ChapterLockEnforcer
import com.example.service.InterceptReason
import com.example.ui.components.FrictionShieldOverlay
import com.example.ui.screens.AppVaultScreen
import com.example.ui.screens.ReadAndLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ActiveFocusSessionState
import com.example.viewmodel.ChallengeMode
import com.example.viewmodel.FrictionChallengeState
import com.example.viewmodel.FrictionReason
import com.example.viewmodel.MathStepProblem
import android.graphics.Canvas
import androidx.compose.ui.platform.ViewRootForTest
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LockIN", appName)
    }

    @Test
    fun `app timer rule blocks when daily limit is reached or reading session is active`() {
        val ruleUnderBudget = AppTimerRuleEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            category = "Social",
            dailyLimitMinutes = 60,
            usedMinutesToday = 45,
            blockDuringReading = true
        )
        assertFalse(ruleUnderBudget.isCurrentlyBlocked(isReadingSessionActive = false))
        assertTrue(ruleUnderBudget.isCurrentlyBlocked(isReadingSessionActive = true))

        val ruleOverBudget = ruleUnderBudget.copy(usedMinutesToday = 60)
        assertTrue(ruleOverBudget.isCurrentlyBlocked(isReadingSessionActive = false))
    }

    @Test
    fun `ChapterLockEnforcer evaluates packages accurately without disk or DB overhead`() {
        val instagramRule = AppTimerRuleEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            category = "Social",
            dailyLimitMinutes = 60,
            usedMinutesToday = 60,
            blockDuringReading = true
        )
        ChapterLockEnforcer.syncRules(listOf(instagramRule))
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

        val igDecision = ChapterLockEnforcer.evaluatePackage("com.instagram.android", "com.example")
        assertNotNull(igDecision)
        assertEquals(InterceptReason.DAILY_LIMIT_EXHAUSTED, igDecision?.reason)
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
    fun `focus lock mode intercepts unauthorized apps while keeping reader open`() {
        val readerPkg = "com.flyersoft.moonreader"
        val now = System.currentTimeMillis()
        ChapterLockEnforcer.updateFocusLockState(
            active = true,
            pinnedPackage = readerPkg,
            pinnedName = "Moon+ Reader",
            endTimeMs = now + 1_800_000L
        )

        val readerDecision = ChapterLockEnforcer.evaluatePackage(readerPkg, "com.example")
        assertNull("Pinned reader must be permitted during focus lock", readerDecision)

        val youtubeDecision = ChapterLockEnforcer.evaluatePackage("com.google.android.youtube", "com.example")
        assertNotNull(youtubeDecision)
        assertEquals(InterceptReason.FOCUS_LOCK_BREACH, youtubeDecision?.reason)
    }

    @Test
    fun `BootReceiver handles broadcast without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `capture real screenshot of AppLimits screen`() {
        composeTestRule.mainClock.autoAdvance = false
        val sampleRules = listOf(
            AppTimerRuleEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                category = "Social",
                dailyLimitMinutes = 45,
                usedMinutesToday = 45
            ),
            AppTimerRuleEntity(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                category = "Video",
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
                        isIgnoringBattery = true,
                        isXiaomi = false,
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
        val readerApp = PinnedFocusAppEntity(
            packageName = "com.flyersoft.moonreader",
            appName = "Moon+ Reader",
            subtitle = "Installed Reader",
            isSelected = true,
            totalFocusedMinutes = 185,
            isInstalled = true
        )
        val activeSession = ActiveFocusSessionState(
            isActive = true,
            pinnedAppName = "Moon+ Reader",
            pinnedPackageName = "com.flyersoft.moonreader",
            selectedDurationMinutes = 45,
            totalSeconds = 2700,
            remainingSeconds = 2142
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 840.dp)) {
                    ReadAndLockScreen(
                        selectedFocusApp = readerApp,
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
            targetAppName = "Moon+ Reader",
            targetPackageName = "com.flyersoft.moonreader",
            mode = ChallengeMode.MEMORY_GRID,
            patternSequence = listOf(2, 5, 10, 13, 7),
            userClickedSequence = listOf(2, 5),
            currentlyFlashingTile = 10,
            isRevealingPattern = false,
            gridStatusMessage = "Tap the 5 tiles in exact order (2 / 5)",
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
        val readerApp = PinnedFocusAppEntity(
            packageName = "com.flyersoft.moonreader",
            appName = "Moon+ Reader",
            subtitle = "Installed Reader",
            isSelected = true,
            totalFocusedMinutes = 240,
            isInstalled = true
        )
        val session = ActiveFocusSessionState(
            isActive = false,
            pinnedAppName = "Moon+ Reader",
            pinnedPackageName = "com.flyersoft.moonreader",
            selectedDurationMinutes = 60,
            totalSeconds = 3600,
            remainingSeconds = 3600
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.size(width = 960.dp, height = 640.dp)) {
                    ReadAndLockScreen(
                        selectedFocusApp = readerApp,
                        activeSession = session,
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
        composeTestRule.mainClock.advanceTimeBy(1000L)
        saveScreenshot("tablet_3_2_layout.png")
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
