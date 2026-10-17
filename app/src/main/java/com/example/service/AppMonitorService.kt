package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ChapterLockDatabase
import com.example.data.ChapterLockRepository
import com.example.data.SystemAppInspector
import com.example.data.formatMinutesClean
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
 * periodic UsageStats synchronization, and fallback foreground app detection.
 */
class AppMonitorService : Service() {

    companion object {
        const val TAG = "AppMonitorService"
        const val CHANNEL_ID = "chapterlock_monitor_channel"
        const val NOTIFICATION_ID = 2001

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
        createNotificationChannel()

        val initialNotification = buildNotification(
            title = "LockIN Active",
            text = "Enforcing daily limits and reading focus guard"
        )

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
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoringLoop() {
        val database = ChapterLockDatabase.getInstance(applicationContext)
        val inspector = SystemAppInspector(applicationContext)
        val repository = ChapterLockRepository(database.dao(), inspector)

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var loopCount = 0L

            while (isActive) {
                try {
                    val isAccessibilityConnected = AppBlockerAccessibilityService.isServiceConnected

                    // 1. Sync screen time stats every 30 seconds
                    if (loopCount % 12L == 0L) {
                        repository.syncSystemUsageStats()
                    }

                    // 2. Notification updates during Focus Lock
                    if (ChapterLockEnforcer.isFocusActive()) {
                        val remainingSecs = ChapterLockEnforcer.getFocusRemainingSeconds()
                        val remainingMins = (remainingSecs / 60).coerceAtLeast(1)
                        val appName = ChapterLockEnforcer.getPinnedAppName()
                        updateNotification(
                            title = "Focus Lock: $appName",
                            text = "${formatMinutesClean(remainingMins)} remaining • Distractions locked"
                        )
                    } else if (loopCount % 24L == 0L) {
                        updateNotification(
                            title = "LockIN Active",
                            text = "Daily app limits guarded"
                        )
                    }

                    // 3. Fallback package inspection only if Accessibility Service is NOT connected
                    if (!isAccessibilityConnected) {
                        val fgPkg = inspector.queryCurrentForegroundPackage()
                        if (fgPkg != null && fgPkg != packageName) {
                            val decision = ChapterLockEnforcer.evaluatePackage(fgPkg, packageName)
                            if (decision != null && !ChapterLockEnforcer.shouldThrottleIntercept(fgPkg)) {
                                val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
                // 2.5s loop for smooth responsiveness while preserving battery
                delay(2500L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_lockin)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(
        title: String = "LockIN Active",
        text: String = "Focus and daily limits guarded"
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
