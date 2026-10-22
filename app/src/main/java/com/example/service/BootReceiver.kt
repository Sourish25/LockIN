package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.ChapterLockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores active Focus Lock countdown notifications after device reboot or app update.
 * When Focus Lock is idle, daily app limits are monitored silently via AccessibilityService
 * without showing any persistent foreground notification.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LockINBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Device rebooted or package replaced ($action). Checking active Focus Lock state.")
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = ChapterLockDatabase.getInstance(appContext).dao()
                    val focusConfig = dao.getFocusConfigOnce()
                    if (focusConfig != null && focusConfig.isStillActive()) {
                        ChapterLockEnforcer.updateFocusLockState(
                            active = true,
                            pinnedPackage = focusConfig.pinnedPackageName,
                            pinnedName = focusConfig.pinnedAppName,
                            endTimeMs = focusConfig.endTimeEpochMs
                        )
                        AppMonitorService.start(appContext)

                        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(focusConfig.pinnedPackageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            try {
                                appContext.startActivity(launchIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to launch pinned app on boot", e)
                            }
                        }
                    } else {
                        LockNotificationManager.refreshForegroundNotification(appContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check Focus Lock state on boot", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
