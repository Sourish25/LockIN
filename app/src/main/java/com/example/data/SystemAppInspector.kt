package com.example.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.example.service.AppBlockerAccessibilityService
import java.util.Calendar

data class DiscoveredDeviceApp(
    val packageName: String,
    val appName: String,
    val category: String,
    val isInstalledOnDevice: Boolean = true,
    val isFocusCandidateApp: Boolean = false,
    val usedMinutesToday: Int = 0
) {
    val isReaderApp: Boolean
        get() = isFocusCandidateApp
}

class SystemAppInspector(val context: Context) {

    // Core OS packages that must never be blocked to prevent locking the user out of the home launcher or system UI
    private val nonBlockableSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.miui.home",
        "com.miui.securitycenter",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.sec.android.app.launcher"
    )

    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            if (mode == AppOpsManager.MODE_DEFAULT) {
                context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hasAccessibilityPermission(): Boolean {
        if (AppBlockerAccessibilityService.isServiceConnected) return true
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledList?.any {
                    it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                            it.resolveInfo?.serviceInfo?.name?.contains(AppBlockerAccessibilityService::class.java.simpleName) == true
                } == true
            ) {
                return true
            }
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val fullName = ComponentName(context, AppBlockerAccessibilityService::class.java).flattenToString()
            val shortName = ComponentName(context, AppBlockerAccessibilityService::class.java).flattenToShortString()
            enabledServices.contains(fullName, ignoreCase = true) ||
                    enabledServices.contains(shortName, ignoreCase = true) ||
                    enabledServices.contains(AppBlockerAccessibilityService::class.java.simpleName, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    fun hasNotificationPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.areNotificationsEnabled() ?: true
            }
        } catch (_: Exception) {
            true
        }
    }

    fun createNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                manufacturer.contains("redmi") || brand.contains("redmi") ||
                manufacturer.contains("poco") || brand.contains("poco")
    }

    fun createUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createOverlaySettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Opens the system App Info page for LockIN so users on Android 13-16 can tap
     * the top-right 3-dot menu -> "Allow restricted settings" if Accessibility or
     * Overlay toggles are greyed out after sideloading the APK.
     */
    fun createAppDetailsSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createBatteryOptimizationIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (directIntent.resolveActivity(context.packageManager) != null) {
                return directIntent
            }
            val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (listIntent.resolveActivity(context.packageManager) != null) {
                return listIntent
            }
            return createAppDetailsSettingsIntent()
        }
        return Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createXiaomiAutostartIntent(): Intent? {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            intent
        } else {
            null
        }
    }

    fun createHomeScreenIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createLaunchIntentForPackage(packageName: String): Intent? {
        if (packageName.isBlank()) return null
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates exact foreground minutes used today per package since local midnight (00:00:00).
     * Combines granular UsageEvents (including the currently active foreground session in real time)
     * with UsageStats buckets so active sessions like watching YouTube increment live every second.
     */
    fun queryTodayUsageMinutesMap(): Map<String, Int> {
        if (!hasUsageStatsPermission()) return emptyMap()
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return emptyMap()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val now = System.currentTimeMillis()

            val eventMillisMap = mutableMapOf<String, Long>()
            val activeResumeMap = mutableMapOf<String, Long>()

            // 1. Exact session calculation from UsageEvents (includes live in-progress foreground session)
            val usageEvents = usageStatsManager.queryEvents(startOfDay, now)
            if (usageEvents != null) {
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val timestamp = event.timeStamp
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            activeResumeMap[pkg] = timestamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            val startTs = activeResumeMap.remove(pkg)
                            if (startTs != null && timestamp > startTs) {
                                val delta = (timestamp - startTs).coerceAtMost(6 * 3600_000L)
                                eventMillisMap[pkg] = (eventMillisMap[pkg] ?: 0L) + delta
                            }
                        }
                    }
                }
            }

            // Include the currently active foreground app's live elapsed time up to `now`
            val currentFgEntry = activeResumeMap.maxByOrNull { it.value }
            if (currentFgEntry != null && now > currentFgEntry.value) {
                val liveDelta = (now - currentFgEntry.value).coerceAtMost(4 * 3600_000L)
                eventMillisMap[currentFgEntry.key] =
                    (eventMillisMap[currentFgEntry.key] ?: 0L) + liveDelta
            }

            // 2. Also query aggregated stats as a floor in case old events were compacted by the OS
            val bucketMillisMap = mutableMapOf<String, Long>()
            val dailyStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            )
            if (dailyStats != null) {
                for (stat in dailyStats) {
                    if (stat.lastTimeUsed >= startOfDay && stat.totalTimeInForeground > 0L) {
                        // Only trust daily bucket directly if its start timestamp belongs to today
                        if (stat.firstTimeStamp >= startOfDay - 3600_000L) {
                            val current = bucketMillisMap[stat.packageName] ?: 0L
                            if (stat.totalTimeInForeground > current) {
                                bucketMillisMap[stat.packageName] = stat.totalTimeInForeground
                            }
                        }
                    }
                }
            }

            // If both are empty (e.g., OEM devices that only populate queryAndAggregateUsageStats)
            if (eventMillisMap.isEmpty() && bucketMillisMap.isEmpty()) {
                val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
                if (!aggregated.isNullOrEmpty()) {
                    for ((pkg, stat) in aggregated) {
                        if (stat.lastTimeUsed >= startOfDay && stat.totalTimeInForeground > 0L) {
                            bucketMillisMap[pkg] = stat.totalTimeInForeground
                        }
                    }
                }
            }

            val allPackages = eventMillisMap.keys + bucketMillisMap.keys
            val resultMap = mutableMapOf<String, Int>()
            for (pkg in allPackages) {
                val totalMs = maxOf(eventMillisMap[pkg] ?: 0L, bucketMillisMap[pkg] ?: 0L)
                val minutes = (totalMs / 60_000L).toInt()
                if (minutes > 0) {
                    resultMap[pkg] = minutes
                }
            }
            resultMap
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Accurately identifies the package currently in the foreground, even during long
     * uninterrupted video playback (up to 2 hours without switching activities).
     */
    fun queryCurrentForegroundPackage(): String? {
        if (!hasUsageStatsPermission()) return null
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return null
            val now = System.currentTimeMillis()

            // 1. Fast check over the last 60 seconds
            findLatestForegroundFromEvents(usageStatsManager, now - 60_000L, now)?.let {
                return it
            }

            // 2. Extended check over the last 2 hours (catches long lecture, study, or deep focus sessions)
            findLatestForegroundFromEvents(usageStatsManager, now - 7_200_000L, now)?.let {
                return it
            }

            // 3. Fallback to UsageStats most recent lastTimeUsed
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 300_000L,
                now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun findLatestForegroundFromEvents(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): String? {
        val events = usageStatsManager.queryEvents(startTime, endTime) ?: return null
        var lastResumedPkg: String? = null
        var lastResumedTime = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= lastResumedTime) {
                    lastResumedTime = event.timeStamp
                    lastResumedPkg = pkg
                }
            }
        }
        return lastResumedPkg
    }

    /**
     * Queries only real, launchable applications installed on the user's device,
     * enriched with today's real screen time usage.
     */
    fun discoverCandidateApps(): List<DiscoveredDeviceApp> {
        val pm = context.packageManager
        val usageMap = queryTodayUsageMinutesMap()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val installedMap = mutableMapOf<String, DiscoveredDeviceApp>()

        // 1. Query all launcher activities
        try {
            val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || pkg in nonBlockableSystemPackages) continue
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                installedMap[pkg] = DiscoveredDeviceApp(
                    packageName = pkg,
                    appName = label,
                    category = categorizePackage(pkg, label),
                    isInstalledOnDevice = true,
                    isFocusCandidateApp = isKnownFocusCandidateApp(pkg, label),
                    usedMinutesToday = usageMap[pkg] ?: 0
                )
            }
        } catch (_: Exception) {
        }

        // 2. Also inspect installed applications with launch intents (covers apps hidden by custom OEM launchers)
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName ?: continue
                if (pkg == context.packageName || pkg in nonBlockableSystemPackages) continue
                if (installedMap.containsKey(pkg)) continue
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    val label = pm.getApplicationLabel(appInfo)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    installedMap[pkg] = DiscoveredDeviceApp(
                        packageName = pkg,
                        appName = label,
                        category = categorizePackage(pkg, label),
                        isInstalledOnDevice = true,
                        isFocusCandidateApp = isKnownFocusCandidateApp(pkg, label),
                        usedMinutesToday = usageMap[pkg] ?: 0
                    )
                }
            }
        } catch (_: Exception) {
        }

        return installedMap.values.sortedWith(
            compareByDescending<DiscoveredDeviceApp> { it.usedMinutesToday }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun isKnownFocusCandidateApp(packageName: String, label: String): Boolean {
        val lower = "${packageName.lowercase()} ${label.lowercase()}"
        return "reader" in lower || "novel" in lower || "book" in lower ||
                "kindle" in lower || "mihon" in lower || "tachiyomi" in lower ||
                "readera" in lower || "kobo" in lower || "webnovel" in lower ||
                "epub" in lower || "manga" in lower || "aniyomi" in lower ||
                "kotatsu" in lower || "lnreader" in lower || "shosetsu" in lower ||
                "librera" in lower || "fbreader" in lower || "lithium" in lower ||
                "youtube" in lower || "coursera" in lower || "udemy" in lower ||
                "khan" in lower || "edx" in lower || "duolingo" in lower ||
                "anki" in lower || "quizlet" in lower || "notion" in lower ||
                "obsidian" in lower || "termux" in lower || "docs" in lower
    }

    fun isEssentialUtilityApp(packageName: String, label: String): Boolean {
        val lower = "${packageName.lowercase()} ${label.lowercase()}"
        return "dialer" in lower || "phone" in lower || "contacts" in lower ||
                "gpay" in lower || "google pay" in lower || "paisa" in lower ||
                "wallet" in lower || "phonepe" in lower || "paytm" in lower ||
                "bank" in lower || "authenticator" in lower || "messages" in lower ||
                "mms" in lower || "calculator" in lower || "maps" in lower
    }

    private fun categorizePackage(packageName: String, label: String): String {
        val lower = "${packageName.lowercase()} ${label.lowercase()}"
        return when {
            "gpay" in lower || "google pay" in lower || "paisa" in lower || "wallet" in lower || "phonepe" in lower || "paytm" in lower || "bank" in lower -> "Payments & Banking"
            "dialer" in lower || "phone" in lower || "contacts" in lower || "emergency" in lower || "messages" in lower || "mms" in lower || "authenticator" in lower -> "Emergency & Calls"
            "duolingo" in lower || "anki" in lower || "khan" in lower || "coursera" in lower || "udemy" in lower || "quizlet" in lower || "edx" in lower || "brilliant" in lower || "learn" in lower || "study" in lower -> "Education & Learning"
            "notion" in lower || "obsidian" in lower || "evernote" in lower || "onenote" in lower || "docs" in lower || "sheets" in lower || "tasks" in lower || "todoist" in lower || "ticktick" in lower || "termux" in lower || "keep" in lower -> "Productivity & Tools"
            "reader" in lower || "novel" in lower || "book" in lower || "kindle" in lower || "mihon" in lower || "tachiyomi" in lower || "readera" in lower || "kobo" in lower || "webnovel" in lower || "epub" in lower || "manga" in lower || "aniyomi" in lower || "kotatsu" in lower || "lnreader" in lower || "shosetsu" in lower || "librera" in lower || "fbreader" in lower || "lithium" in lower -> "Reading & Reference"
            "youtube" in lower || "netflix" in lower || "video" in lower || "twitch" in lower || "bilibili" in lower || "prime" in lower || "disney" in lower || "hotstar" in lower -> "Video & Streaming"
            "instagram" in lower || "twitter" in lower || "facebook" in lower || "threads" in lower || "discord" in lower || "whatsapp" in lower || "telegram" in lower || "snapchat" in lower -> "Social & Messaging"
            "tiktok" in lower || "musical" in lower || "reel" in lower || "shorts" in lower -> "Short Video"
            "reddit" in lower || "news" in lower || "forum" in lower || "quora" in lower -> "Forums & News"
            "chrome" in lower || "browser" in lower || "firefox" in lower || "brave" in lower || "opera" in lower || "edge" in lower -> "Browser"
            "game" in lower || "genshin" in lower || "roblox" in lower || "clash" in lower || "steam" in lower -> "Games"
            else -> "Installed App"
        }
    }
}
