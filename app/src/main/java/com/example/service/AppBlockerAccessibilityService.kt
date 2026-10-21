package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ChapterLockDatabase
import com.example.data.ChapterLockRepository
import com.example.data.SystemAppInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time Accessibility Service that intercepts distraction apps and enforces
 * Focus Lock and Daily Limits via both native WindowManager overlays and MainActivity.
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        Log.i(TAG, "LockIN Accessibility Service connected.")

        createNotificationChannel()

        val db = ChapterLockDatabase.getInstance(applicationContext)
        val inspector = SystemAppInspector(applicationContext)
        val repo = ChapterLockRepository(db.dao(), inspector)

        // 1. Load rules and active focus state from SQLite
        serviceScope.launch {
            try {
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

        // 3. Continuous 500ms active window & live usage enforcement loop
        // Ensures that even if YouTube is already open when its daily timer expires,
        // or if an app switch skips TYPE_WINDOW_STATE_CHANGED, it is blocked within 0.5s.
        enforcementLoopJob?.cancel()
        enforcementLoopJob = serviceScope.launch {
            var tick = 0L
            while (isActive) {
                try {
                    if (tick % 4L == 0L) {
                        repo.syncSystemUsageStats()
                        LockNotificationManager.checkAndNotifyLimitMilestones(
                            applicationContext,
                            repo.getAllAppRulesOnce()
                        )
                    }
                    val fgPkg = inspector.queryCurrentForegroundPackage()
                    if (!fgPkg.isNullOrBlank()) {
                        inspectAndEnforcePackage(fgPkg)
                    }
                } catch (_: Exception) {
                }
                tick++
                delay(500L)
            }
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

        val candidatePkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: return
        inspectAndEnforcePackage(candidatePkg)
    }

    private fun inspectAndEnforcePackage(packageName: String) {
        val myPackageName = applicationContext.packageName
        if (packageName == myPackageName) return

        val decision = ChapterLockEnforcer.evaluatePackage(packageName, myPackageName) ?: return

        if (MainActivity.isInForeground || LockWindowOverlayManager.isShowingFor(packageName)) {
            return
        }

        if (ChapterLockEnforcer.shouldThrottleIntercept(packageName) && LockWindowOverlayManager.isShowing()) {
            return
        }

        Log.w(TAG, "Intercepting package: $packageName for reason: ${decision.reason}")

        // 1. Immediately display the System Window Overlay Barrier over the target app
        // (Uses TYPE_APPLICATION_OVERLAY or TYPE_ACCESSIBILITY_OVERLAY so OEM BAL cannot block it)
        LockWindowOverlayManager.showBarrier(
            context = this,
            decision = decision,
            isFromAccessibilityService = true
        )

        // 2. Also launch MainActivity Friction Shield directly (without calling GLOBAL_ACTION_HOME first,
        // which would trigger Android's 5-second stopAppSwitches BAL lock)
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
            Log.e(TAG, "Direct startActivity suppressed; overlay barrier active", e)
        }

        // 3. Post high-priority FullScreenIntent notification
        try {
            showInterceptNotification(decision.appName, decision.reason, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show intercept notification", e)
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
