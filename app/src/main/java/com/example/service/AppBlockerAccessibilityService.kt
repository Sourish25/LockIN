package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ChapterLockDatabase
import com.example.data.ChapterLockRepository
import com.example.data.SystemAppInspector
import com.example.ui.theme.ExpressiveFeedbackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time Accessibility Service that enforces Single-App Focus Lock pinning,
 * navigation key/gesture interception, PiP & background media suppression,
 * and daily screen time limits.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AppBlockerService"
        const val INTERCEPT_CHANNEL_ID = "chapterlock_intercept_channel"
        const val INTERCEPT_NOTIFICATION_ID = 2002

        @Volatile
        var isServiceConnected: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var enforcementLoopJob: Job? = null

    @Volatile
    private var currentForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        Log.i(TAG, "LockIN Accessibility Service connected with Key Filtering and Interactive Window tracking enabled.")

        // Configure runtime accessibility flags to ensure window content & key events are available
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply dynamic accessibility service flags", e)
        }

        createNotificationChannel()

        val db = ChapterLockDatabase.getInstance(applicationContext)
        val inspector = SystemAppInspector(applicationContext)
        val repo = ChapterLockRepository(db.dao(), inspector)

        // 1. Discover launchers & candidate apps, then observe app timer rules
        serviceScope.launch {
            try {
                discoverAndRegisterLaunchers()
                repo.ensureSeededAndSynced()
                val discovered = inspector.discoverCandidateApps()
                ChapterLockEnforcer.registerAppLabels(
                    discovered.associate { it.packageName to it.appName }
                )

                db.dao().observeAppRules().collect { rules ->
                    ChapterLockEnforcer.syncRules(rules)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize rules in accessibility service", e)
            }
        }

        // 2. Continuously observe focus config changes
        serviceScope.launch {
            try {
                db.dao().observeFocusConfig().collect { config ->
                    if (config != null) {
                        val active = config.isStillActive()
                        ChapterLockEnforcer.updateFocusLockState(
                            active = active,
                            pinnedPackage = config.pinnedPackageName,
                            pinnedName = config.pinnedAppName,
                            endTimeMs = config.endTimeEpochMs
                        )
                        if (active) {
                            AppMonitorService.start(applicationContext)
                        }
                        LockNotificationManager.refreshForegroundNotification(applicationContext)
                    } else {
                        LockNotificationManager.refreshForegroundNotification(applicationContext)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to observe focus config in accessibility service", e)
            }
        }

        // 2b. Continuously observe Focus Lock exception apps (Emergency Calls, GPay, etc.)
        serviceScope.launch {
            try {
                db.dao().observeFocusExceptionApps().collect { exceptions ->
                    ChapterLockEnforcer.syncFocusExceptions(exceptions.map { it.packageName })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to observe focus exception apps in accessibility service", e)
            }
        }

        // 3. Periodic background sync and PiP window cleanup loop (2000ms cadence)
        enforcementLoopJob?.cancel()
        enforcementLoopJob = serviceScope.launch {
            var tick = 0L
            while (isActive) {
                try {
                    if (tick % 3L == 0L) {
                        repo.syncSystemUsageStats()
                        LockNotificationManager.checkAndNotifyLimitMilestones(
                            applicationContext,
                            repo.getAllAppRulesOnce()
                        )
                    }
                    // Scan interactive windows for Picture-in-Picture floating windows
                    inspectInteractiveWindows()
                } catch (_: Exception) {
                }
                tick++
                delay(2000L)
            }
        }
    }

    private fun discoverAndRegisterLaunchers() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfos = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            val launchers = resolveInfos.mapNotNull { it.activityInfo?.packageName }
            ChapterLockEnforcer.registerDetectedLaunchers(launchers)
            Log.i(TAG, "Discovered and registered ${launchers.size} launcher package(s).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover launchers", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            inspectInteractiveWindows()
            return
        }

        val candidatePkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: return

        // Do not update foreground package or enforce if event is from soft keyboard or system notification shade
        if (candidatePkg == "com.android.systemui" ||
            candidatePkg.contains("inputmethod", ignoreCase = true)
        ) {
            return
        }

        currentForegroundPackage = candidatePkg
        inspectAndEnforcePackage(candidatePkg)
    }

    /**
     * Intercepts hardware and virtual keys when Focus Lock is active:
     * - KEYCODE_BACK: Allowed within pinned app and exception apps so internal navigation is smooth.
     * - KEYCODE_HOME: Consumed and smoothly bounces back to pinned app.
     * - KEYCODE_APP_SWITCH: Consumed to block the recent apps task switcher.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!ChapterLockEnforcer.isFocusActive() || MainActivity.isInForeground) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode
        val action = event.action
        val currentFg = currentForegroundPackage.orEmpty()
        val pinnedPkg = ChapterLockEnforcer.getPinnedFocusPackage()
        val myPkg = applicationContext.packageName

        // Never intercept keys when user is actively inside LockIN
        if (currentFg == myPkg) {
            return super.onKeyEvent(event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                // Inside pinned focus app or allowed exception apps:
                // Allow back events to pass through so internal navigation (menus, search, previous pages) works smoothly!
                if (currentFg == pinnedPkg || ChapterLockEnforcer.isFocusExceptionPackage(currentFg)) {
                    return super.onKeyEvent(event)
                }

                // If user is anywhere else (e.g. attempting to navigate out): bounce back to pinned app
                if (action == KeyEvent.ACTION_DOWN && pinnedPkg.isNotBlank() && currentFg != pinnedPkg && currentFg != myPkg) {
                    bounceBackToPinnedApp(pinnedPkg)
                }
                return true
            }

            KeyEvent.KEYCODE_HOME -> {
                if (action == KeyEvent.ACTION_DOWN && pinnedPkg.isNotBlank() && currentFg != myPkg) {
                    bounceBackToPinnedApp(pinnedPkg)
                }
                return true
            }

            KeyEvent.KEYCODE_APP_SWITCH -> {
                if (action == KeyEvent.ACTION_DOWN && currentFg != myPkg) {
                    ExpressiveFeedbackEngine.playInterceptionRejected(applicationContext)
                    if (pinnedPkg.isNotBlank()) {
                        bounceBackToPinnedApp(pinnedPkg)
                    }
                }
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    /**
     * Scans all interactive windows via WindowManager to detect Picture-in-Picture (PiP),
     * split-screen panes, or floating freeform windows running blocked apps.
     */
    private fun inspectInteractiveWindows() {
        if (MainActivity.isInForeground) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val windowList = try { windows } catch (_: Exception) { null } ?: return

        val myPkg = applicationContext.packageName
        val currentFg = currentForegroundPackage.orEmpty()
        val isOurAppOrSettings = currentFg == myPkg ||
                currentFg == "com.android.settings" ||
                currentFg.contains("settings", ignoreCase = true)

        for (window in windowList) {
            val isPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.isInPictureInPictureMode
            } else false

            // If user is inside our app or settings, ONLY inspect floating PiP windows to mute background video
            if (isOurAppOrSettings && !isPip) {
                continue
            }

            // Critical optimization: ignore inactive, unfocused background windows (such as background launcher)
            if (!isPip && !window.isActive && !window.isFocused) {
                continue
            }

            val rootNode = try { window.root } catch (_: Exception) { null }
            val pkg = rootNode?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: continue
            if (pkg == myPkg) continue

            // Never intercept SystemUI, pinned focus app, or allowed focus exception apps
            if (ChapterLockEnforcer.isPackageAllowedDuringFocus(pkg)) {
                continue
            }

            val decision = ChapterLockEnforcer.evaluatePackage(pkg, myPkg)
            if (decision != null) {
                // If this is a launcher breach but user is actually inside our app or settings, ignore it!
                if (decision.reason == InterceptReason.FOCUS_LOCK_LAUNCHER_BREACH && isOurAppOrSettings) {
                    continue
                }

                // 1. Instantly mute audio & stop media playback
                MediaPlaybackSuppressor.suppressPlayback(this, pkg)

                // 2. Force close PiP window if running in Picture-in-Picture mode
                if (isPip) {
                    dismissPipWindow(rootNode)
                }

                // 3. Enforce barrier overlay & return to pinned app
                inspectAndEnforcePackage(pkg)
                break
            }
        }
    }

    private fun dismissPipWindow(root: AccessibilityNodeInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                root.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
            } else {
                @Suppress("DEPRECATION")
                root.performAction(AccessibilityNodeInfo.ACTION_DISMISS)
            }
            findAndClickDismissButton(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing PiP window", e)
        }
    }

    private fun findAndClickDismissButton(node: AccessibilityNodeInfo): Boolean {
        val desc = (node.contentDescription ?: "").toString().lowercase()
        val res = (node.viewIdResourceName ?: "").lowercase()
        if (desc.contains("close") || desc.contains("dismiss") ||
            res.contains("close") || res.contains("dismiss") || res.contains("pip_dismiss")
        ) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickDismissButton(child)) {
                return true
            }
        }
        return false
    }

    private var lastBounceBackTime = 0L

    private fun inspectAndEnforcePackage(packageName: String) {
        val myPackageName = applicationContext.packageName
        if (packageName == myPackageName || MainActivity.isInForeground) return

        // Skip SystemUI (notification shade / quick settings) and keyboards
        if (packageName == "com.android.systemui" || packageName.contains("inputmethod", ignoreCase = true)) {
            return
        }

        val decision = ChapterLockEnforcer.evaluatePackage(packageName, myPackageName) ?: return

        // 1. If user attempted to navigate to the Home screen launcher during Focus Lock:
        // Execute an immediate smooth bounce back to the pinned focus app!
        // DO NOT show overlay barrier, DO NOT start MainActivity, DO NOT post full screen intent!
        if (decision.reason == InterceptReason.FOCUS_LOCK_LAUNCHER_BREACH) {
            if (MainActivity.isInForeground) return
            val pinnedPkg = ChapterLockEnforcer.getPinnedFocusPackage()
            if (pinnedPkg.isNotBlank()) {
                bounceBackToPinnedApp(pinnedPkg)
                return
            }
        }

        // 2. Suppress media playback immediately for any intercepted application
        MediaPlaybackSuppressor.suppressPlayback(this, decision.packageName)

        if (MainActivity.isInForeground || LockWindowOverlayManager.isShowingFor(packageName)) {
            return
        }

        if (ChapterLockEnforcer.shouldThrottleIntercept(packageName) && LockWindowOverlayManager.isShowing()) {
            return
        }

        Log.w(TAG, "Intercepting package: $packageName for reason: ${decision.reason}")

        // 3. Display the clean System Window Overlay Barrier if permission is granted
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (canDrawOverlay) {
            LockWindowOverlayManager.showBarrier(
                context = this,
                decision = decision,
                isFromAccessibilityService = true
            )
        } else {
            // Fallback: Launch MainActivity Friction Shield directly ONLY when overlay permission is missing
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE, decision.packageName)
                putExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON, decision.reason.name)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch friction challenge activity, falling back to notification", e)
                showInterceptNotification(decision.appName, decision.reason, intent)
            }
        }
    }

    private fun bounceBackToPinnedApp(pinnedPackage: String) {
        if (MainActivity.isInForeground) return
        val myPkg = applicationContext.packageName
        if (currentForegroundPackage == myPkg || currentForegroundPackage == pinnedPackage) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBounceBackTime < 800L) {
            return
        }
        lastBounceBackTime = now

        val launchIntent = packageManager.getLaunchIntentForPackage(pinnedPackage)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        } ?: return

        try {
            startActivity(launchIntent)
            ExpressiveFeedbackEngine.playInterceptionRejected(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bounce back to pinned app: $pinnedPackage", e)
        }
    }

    private fun showInterceptNotification(
        appName: String,
        reason: InterceptReason,
        targetIntent: Intent
    ) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            101,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (reason == InterceptReason.FOCUS_LOCK_BREACH) {
            "Focus Lock Active: $appName Blocked"
        } else {
            "Daily Limit Reached: $appName"
        }

        val text = if (reason == InterceptReason.FOCUS_LOCK_BREACH) {
            "Return to your pinned focus app or solve the puzzle."
        } else {
            "Daily screen time exhausted. Tap to view unlock puzzle."
        }

        val notification = NotificationCompat.Builder(this, INTERCEPT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lockin)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(INTERCEPT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                INTERCEPT_CHANNEL_ID,
                "LockIN App Interception",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate barrier when a blocked app is opened"
                setBypassDnd(true)
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "LockIN Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        isServiceConnected = false
        enforcementLoopJob?.cancel()
        serviceScope.cancel()
        LockWindowOverlayManager.dismiss()
        super.onDestroy()
        Log.i(TAG, "LockIN Accessibility Service destroyed.")
    }
}
