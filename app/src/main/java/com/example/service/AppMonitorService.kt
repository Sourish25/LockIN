package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.MainActivity
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
 * Foreground Service that maintains LockIN's ongoing system presence,
 * live 2.4-second UsageStats synchronization, milestone notifications,
 * and continuous 600ms foreground app enforcement.
 */
class AppMonitorService : Service() {

    companion object {
        const val TAG = "AppMonitorService"
        const val CHANNEL_ID = LockNotificationManager.CHANNEL_GUARD_ID
        const val NOTIFICATION_ID = LockNotificationManager.NOTIFICATION_ID_FOREGROUND

        fun start(context: Context) {
            try {
                val intent = Intent(context, AppMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AppMonitorService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        LockNotificationManager.ensureChannels(this)

        val initialNotification = LockNotificationManager.buildForegroundServiceNotification(this)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error", e)
            try {
                startForeground(NOTIFICATION_ID, initialNotification)
            } catch (_: Exception) {
            }
        }

        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob?.isActive != true) {
            startMonitoringLoop()
        } else {
            LockNotificationManager.refreshForegroundNotification(this)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoringLoop() {
        val database = ChapterLockDatabase.getInstance(applicationContext)
        val dao = database.dao()
        val inspector = SystemAppInspector(applicationContext)
        val repository = ChapterLockRepository(dao, inspector)

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            try {
                repository.ensureSeededAndSynced()
                val discovered = inspector.discoverCandidateApps()
                ChapterLockEnforcer.registerAppLabels(
                    discovered.associate { it.packageName to it.appName }
                )
                LockNotificationManager.refreshForegroundNotification(applicationContext)
            } catch (_: Exception) {
            }

            var loopCount = 0L

            while (isActive) {
                try {
                    // 1. Sync live screen time stats & check 80%/100% notification milestones every ~2.4 seconds
                    if (loopCount % 4L == 0L) {
                        repository.syncSystemUsageStats()
                        val currentRules = repository.getAllAppRulesOnce()
                        LockNotificationManager.checkAndNotifyLimitMilestones(applicationContext, currentRules)
                    }

                    // 2. Check if an active Focus Lock session naturally completed in the background
                    val focusConfig = dao.getFocusConfigOnce()
                    if (focusConfig != null && focusConfig.isActive && !focusConfig.isStillActive()) {
                        val completedName = focusConfig.pinnedAppName.ifBlank { "Focus App" }
                        val completedPkg = focusConfig.pinnedPackageName
                        val completedDuration = focusConfig.selectedDurationMinutes
                        repository.endFocusLockSession(earlyQuit = false)
                        LockNotificationManager.notifyFocusSessionCompleted(
                            context = applicationContext,
                            completedAppName = completedName,
                            completedPackageName = completedPkg,
                            durationMinutes = completedDuration
                        )
                    } else if (loopCount % 5L == 0L) {
                        LockNotificationManager.refreshForegroundNotification(applicationContext)
                    }

                    // 3. Always inspect foreground package via UsageEvents (reinforces AccessibilityService)
                    val fgPkg = inspector.queryCurrentForegroundPackage()
                    if (!fgPkg.isNullOrBlank() && fgPkg != packageName) {
                        val decision = ChapterLockEnforcer.evaluatePackage(fgPkg, packageName)
                        if (decision != null &&
                            !MainActivity.isInForeground &&
                            !LockWindowOverlayManager.isShowingFor(fgPkg)
                        ) {
                            if (!ChapterLockEnforcer.shouldThrottleIntercept(fgPkg) ||
                                !LockWindowOverlayManager.isShowing()
                            ) {
                                LockWindowOverlayManager.showBarrier(
                                    context = applicationContext,
                                    decision = decision,
                                    isFromAccessibilityService = false
                                )

                                val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
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
                                    startActivity(openIntent)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                loopCount++
                delay(600L)
            }
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
