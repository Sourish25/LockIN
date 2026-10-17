package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Ensures ChapterLock automatically restores background guarding, app limit enforcement,
 * and light novel focus state after the device reboots or the app updates.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ChapterLockBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Device rebooted or package replaced ($action). Restoring ChapterLock guard.")
            try {
                AppMonitorService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AppMonitorService on boot", e)
            }
        }
    }
}
