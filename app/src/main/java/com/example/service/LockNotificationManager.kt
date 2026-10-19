package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppTimerRuleEntity
import com.example.data.formatMinutesClean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized notification engine for LockIN.
 * Connects Focus Lock live countdowns, 80% / <=5m daily limit warnings,
 * daily limit exhaustion alerts, and session completion notifications
 * with interactive broadcast actions.
 */
object LockNotificationManager {

    const val CHANNEL_GUARD_ID = "chapterlock_monitor_channel"
    const val CHANNEL_WARNINGS_ID = "lockin_limit_warnings"
    const val CHANNEL_INTERCEPT_ID = "lockin_shield_alerts"

    const val NOTIFICATION_ID_FOREGROUND = 2001
    const val NOTIFICATION_ID_FOCUS_COMPLETED = 4045
    private const val BASE_WARNING_NOTIFICATION_ID = 5000
    private const val BASE_EXHAUSTED_NOTIFICATION_ID = 6000

    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val EXTRA_FORCE_EARLY_EXIT_CHALLENGE = "extra_force_early_exit_challenge"
    const val TAB_APP_LIMITS = "APP_LIMITS"
    const val TAB_FOCUS_LOCK = "FOCUS_LOCK"

    // Tracks which packages have already triggered an 80% warning or 100% exhaustion alert today
    private val warnedPackagesToday = ConcurrentHashMap<String, String>()
    private val exhaustedNotifiedPackagesToday = ConcurrentHashMap<String, String>()

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val guardChannel = NotificationChannel(
            CHANNEL_GUARD_ID,
            "LockIN Active Guard & Live Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows live Focus Lock countdowns and active daily limit protection."
            setShowBadge(false)
        }

        val warningsChannel = NotificationChannel(
            CHANNEL_WARNINGS_ID,
            "Screen Time Warnings & Session Milestones",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when an app has 5 minutes left today or when a reading Focus Lock finishes."
            enableVibration(true)
        }

        val interceptChannel = NotificationChannel(
            CHANNEL_INTERCEPT_ID,
            "LockIN Interception Shield",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Immediate alerts when a daily app limit is reached or an unapproved app is blocked."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }

        nm.createNotificationChannel(guardChannel)
        nm.createNotificationChannel(warningsChannel)
        nm.createNotificationChannel(interceptChannel)
    }

    /**
     * Builds the dynamic foreground notification for AppMonitorService.
     * - When Focus Lock is running: transforms into a live countdown chronometer with
     *   [Open App], [+15m], and [Unlock (Puzzle)] action buttons.
     * - When idle: summarizes active app limits and how many apps are locked today,
     *   with [App Limits] and [Focus Lock] action shortcuts.
     */
    fun buildForegroundServiceNotification(context: Context): Notification {
        ensureChannels(context)

        val focusActive = ChapterLockEnforcer.isFocusModeCurrentlyActive()
        val endTimeMillis = ChapterLockEnforcer.getFocusEndTimeEpochMillis()
        val pinnedPackage = ChapterLockEnforcer.getWhitelistedReaderPackage()
        val pinnedAppName = ChapterLockEnforcer.getActiveFocusAppName().ifBlank { "Focus App" }

        if (focusActive && endTimeMillis > System.currentTimeMillis()) {
            val remainingMins = ((endTimeMillis - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TAB, TAB_FOCUS_LOCK)
            }
            val mainPending = PendingIntent.getActivity(
                context,
                401,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action 1: Open Pinned App
            val openPinnedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_OPEN_PINNED_APP
                putExtra(NotificationActionReceiver.EXTRA_PACKAGE_NAME, pinnedPackage)
            }
            val openPinnedPending = PendingIntent.getBroadcast(
                context,
                402,
                openPinnedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action 2: Extend +15m
            val extendIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_EXTEND_FOCUS_15M
            }
            val extendPending = PendingIntent.getBroadcast(
                context,
                403,
                extendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action 3: Early Unlock (via Puzzle)
            val unlockIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_FORCE_EARLY_EXIT_CHALLENGE, true)
            }
            val unlockPending = PendingIntent.getActivity(
                context,
                404,
                unlockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_GUARD_ID)
                .setSmallIcon(R.drawable.ic_stat_lockin)
                .setContentTitle("Focus Lock: $pinnedAppName")
                .setContentText("Pinned to $pinnedAppName (${formatMinutesClean(remainingMins)} left) • Other apps blocked")
                .setSubText("Focus Lock Active")
                .setContentIntent(mainPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setWhen(endTimeMillis)
                .setUsesChronometer(true)
                .addAction(0, "Open $pinnedAppName", openPinnedPending)
                .addAction(0, "+15m", extendPending)
                .addAction(0, "Unlock (Puzzle)", unlockPending)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
            return builder.build()
        } else {
            val rules = ChapterLockEnforcer.getCachedRulesSnapshot()
            val enabledRules = rules.filter { it.isDailyLimitEnabled }
            val lockedCount = enabledRules.count { it.isOverDailyBudget && !it.isTemporarilyUnlocked() }

            val title = when {
                lockedCount > 0 -> "LockIN Guard • $lockedCount ${if (lockedCount == 1) "app" else "apps"} locked today"
                enabledRules.isNotEmpty() -> "LockIN Guard • ${enabledRules.size} daily ${if (enabledRules.size == 1) "limit" else "limits"} active"
                else -> "LockIN Guard Active"
            }

            val body = when {
                lockedCount > 0 && enabledRules.isNotEmpty() -> {
                    val lockedNames = enabledRules
                        .filter { it.isOverDailyBudget && !it.isTemporarilyUnlocked() }
                        .joinToString(", ") { it.appName }
                    "Blocked for today: $lockedNames"
                }
                enabledRules.isNotEmpty() -> {
                    val topRule = enabledRules.maxByOrNull { it.usageProgress }
                    if (topRule != null && topRule.usedMinutesToday > 0) {
                        "${topRule.appName}: ${formatMinutesClean(topRule.usedMinutesToday)} / ${formatMinutesClean(topRule.dailyLimitMinutes)} used today"
                    } else {
                        "Monitoring daily screen time budgets in real time."
                    }
                }
                else -> "Tap to configure daily app limits or start a Single-App Focus Lock."
            }

            val openVaultIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TAB, TAB_APP_LIMITS)
            }
            val openVaultPending = PendingIntent.getActivity(
                context,
                405,
                openVaultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openFocusIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TAB, TAB_FOCUS_LOCK)
            }
            val openFocusPending = PendingIntent.getActivity(
                context,
                406,
                openFocusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_GUARD_ID)
                .setSmallIcon(R.drawable.ic_stat_lockin)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openVaultPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "App Limits", openVaultPending)
                .addAction(0, "Focus Lock", openFocusPending)
                .build()
        }
    }

    /**
     * Immediately updates the ongoing foreground service notification to reflect the latest
     * Focus Lock timer or daily app limit counters.
     */
    fun refreshForegroundNotification(context: Context) {
        try {
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(NOTIFICATION_ID_FOREGROUND, buildForegroundServiceNotification(context))
        } catch (_: Exception) {
        }
    }

    /**
     * Evaluates updated daily app rules and fires non-spammy notifications when:
     * 1. An app reaches >= 80% of its daily budget (or <= 5 minutes remaining).
     * 2. An app reaches 100% of its daily budget for the first time today.
     */
    fun checkAndNotifyLimitMilestones(context: Context, rules: List<AppTimerRuleEntity>) {
        try {
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val today = todayKey()

            for (rule in rules) {
                if (!rule.isDailyLimitEnabled) continue

                val remaining = rule.remainingMinutesToday
                val fraction = rule.usageProgress

                if (rule.isOverDailyBudget) {
                    if (exhaustedNotifiedPackagesToday[rule.packageName] != today) {
                        exhaustedNotifiedPackagesToday[rule.packageName] = today
                        nm.cancel(BASE_WARNING_NOTIFICATION_ID + (rule.packageName.hashCode() and 0x7FF))
                        postLimitExhaustedNotification(context, nm, rule)
                    }
                } else if (rule.usedMinutesToday > 0 && (fraction >= 0.8f || (rule.dailyLimitMinutes > 5 && remaining in 1..5))) {
                    if (warnedPackagesToday[rule.packageName] != today) {
                        warnedPackagesToday[rule.packageName] = today
                        postApproachingLimitNotification(context, nm, rule)
                    }
                }
            }

            refreshForegroundNotification(context)
        } catch (_: Exception) {
        }
    }

    private fun postApproachingLimitNotification(
        context: Context,
        nm: NotificationManager,
        rule: AppTimerRuleEntity
    ) {
        val notifId = BASE_WARNING_NOTIFICATION_ID + (rule.packageName.hashCode() and 0x7FF)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, TAB_APP_LIMITS)
        }
        val openAppPending = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lockNowIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_LOCK_APP_NOW
            putExtra(NotificationActionReceiver.EXTRA_PACKAGE_NAME, rule.packageName)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val lockNowPending = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            lockNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS_ID)
            .setSmallIcon(R.drawable.ic_stat_lockin)
            .setContentTitle("${formatMinutesClean(rule.remainingMinutesToday)} left on ${rule.appName} today")
            .setContentText("${formatMinutesClean(rule.usedMinutesToday)} of ${formatMinutesClean(rule.dailyLimitMinutes)} daily limit used.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPending)
            .addAction(0, "Lock Now", lockNowPending)
            .addAction(0, "Manage Limit", openAppPending)
            .build()

        nm.notify(notifId, notification)
    }

    private fun postLimitExhaustedNotification(
        context: Context,
        nm: NotificationManager,
        rule: AppTimerRuleEntity
    ) {
        val notifId = BASE_EXHAUSTED_NOTIFICATION_ID + (rule.packageName.hashCode() and 0x7FF)

        val puzzleIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ChapterLockEnforcer.EXTRA_INTERCEPTED_PACKAGE, rule.packageName)
            putExtra(ChapterLockEnforcer.EXTRA_INTERCEPT_REASON, InterceptReason.DAILY_LIMIT_EXHAUSTED.name)
        }
        val puzzlePending = PendingIntent.getActivity(
            context,
            notifId,
            puzzleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INTERCEPT_ID)
            .setSmallIcon(R.drawable.ic_stat_lockin)
            .setContentTitle("${rule.appName} locked for today")
            .setContentText("Daily ${formatMinutesClean(rule.dailyLimitMinutes)} limit reached. Solve the cognitive puzzle for a 10m pass.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(puzzlePending)
            .addAction(0, "10m Pass (Puzzle)", puzzlePending)
            .build()

        nm.notify(notifId, notification)
    }

    /**
     * Fires when a Single-App Focus Lock session completes its full countdown naturally.
     */
    fun notifyFocusSessionCompleted(
        context: Context,
        completedAppName: String,
        completedPackageName: String,
        durationMinutes: Int
    ) {
        try {
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val openMainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TAB, TAB_FOCUS_LOCK)
            }
            val openMainPending = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_FOCUS_COMPLETED,
                openMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val bonusIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_START_BONUS_FOCUS_15M
                putExtra(NotificationActionReceiver.EXTRA_PACKAGE_NAME, completedPackageName)
                putExtra(NotificationActionReceiver.EXTRA_APP_NAME, completedAppName)
            }
            val bonusPending = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_FOCUS_COMPLETED + 1,
                bonusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openAppIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_OPEN_PINNED_APP
                putExtra(NotificationActionReceiver.EXTRA_PACKAGE_NAME, completedPackageName)
            }
            val openAppPending = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_FOCUS_COMPLETED + 2,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS_ID)
                .setSmallIcon(R.drawable.ic_stat_lockin)
                .setContentTitle("Focus Lock Completed")
                .setContentText("You finished your ${formatMinutesClean(durationMinutes)} focus session in $completedAppName.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(openMainPending)
                .addAction(0, "+15m Bonus Lock", bonusPending)
                .addAction(0, "Open $completedAppName", openAppPending)
                .build()

            nm.notify(NOTIFICATION_ID_FOCUS_COMPLETED, notification)
            refreshForegroundNotification(context)
        } catch (_: Exception) {
        }
    }
}
