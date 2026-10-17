package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restores LockIN background guarding, daily app limit enforcement,
 * and active reading focus sessions after device reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LockINBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Device rebooted or package replaced ($action). Restoring LockIN guard.")
            try {
                AppMonitorService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AppMonitorService on boot", e)
            }
        }
    }
}
