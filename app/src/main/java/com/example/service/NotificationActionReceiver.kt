package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.ChapterLockDatabase
import com.example.data.ChapterLockRepository
import com.example.data.SystemAppInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles interactive notification action buttons from LockNotificationManager:
 * - [ACTION_OPEN_PINNED_APP]: Launches the currently pinned Focus Lock app.
 * - [ACTION_EXTEND_FOCUS_15M]: Adds +15 minutes to the active Focus Lock session in Room & Enforcer.
 * - [ACTION_START_BONUS_FOCUS_15M]: Starts a new 15-minute Focus Lock session for the completed app.
 * - [ACTION_LOCK_APP_NOW]: Caps an approaching-limit app's daily limit at its current usage immediately.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_OPEN_PINNED_APP = "com.example.action.OPEN_PINNED_APP"
        const val ACTION_EXTEND_FOCUS_15M = "com.example.action.EXTEND_FOCUS_15M"
        const val ACTION_START_BONUS_FOCUS_15M = "com.example.action.START_BONUS_FOCUS_15M"
        const val ACTION_LOCK_APP_NOW = "com.example.action.LOCK_APP_NOW"

        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = ChapterLockDatabase.getInstance(appContext)
                val inspector = SystemAppInspector(appContext)
                val repo = ChapterLockRepository(db.dao(), inspector)

                when (action) {
                    ACTION_OPEN_PINNED_APP -> {
                        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                            ?.ifBlank { ChapterLockEnforcer.getPinnedFocusPackage() }
                            ?: ChapterLockEnforcer.getPinnedFocusPackage()
                        if (pkg.isNotBlank()) {
                            val launchIntent = repo.createLaunchIntent(pkg)
                            if (launchIntent != null) {
                                appContext.startActivity(launchIntent)
                            }
                        }
                    }

                    ACTION_EXTEND_FOCUS_15M -> {
                        repo.extendActiveFocusSession(15)
                        LockNotificationManager.refreshForegroundNotification(appContext)
                    }

                    ACTION_START_BONUS_FOCUS_15M -> {
                        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.cancel(LockNotificationManager.NOTIFICATION_ID_FOCUS_COMPLETED)

                        if (pkg.isNotBlank()) {
                            repo.startFocusLockSession(
                                fallbackPackageName = pkg,
                                fallbackAppName = appName.ifBlank { pkg.substringAfterLast('.') },
                                overrideDurationMinutes = 15
                            )
                            LockNotificationManager.refreshForegroundNotification(appContext)
                            val launchIntent = repo.createLaunchIntent(pkg)
                            if (launchIntent != null) {
                                appContext.startActivity(launchIntent)
                            }
                        }
                    }

                    ACTION_LOCK_APP_NOW -> {
                        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                        if (notifId != -1) {
                            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(notifId)
                        }
                        if (pkg.isNotBlank()) {
                            repo.syncSystemUsageStats()
                            val rules = repo.getAllAppRulesOnce()
                            val targetRule = rules.find { it.packageName == pkg }
                            if (targetRule != null) {
                                val lockedLimit = targetRule.usedMinutesToday.coerceAtLeast(1)
                                repo.updateAppDailyLimit(pkg, lockedLimit)
                                val updatedRules = repo.getAllAppRulesOnce()
                                LockNotificationManager.checkAndNotifyLimitMilestones(appContext, updatedRules)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationActionReceiver", "Failed to handle notification action", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
