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
    val isReaderApp: Boolean = false
)

class SystemAppInspector(private val context: Context) {

    // Core OS packages that must never be blocked to prevent locking the user out of device settings or home screen
    private val nonBlockableSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
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
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun hasAccessibilityPermission(): Boolean {
        if (AppBlockerAccessibilityService.isServiceConnected) return true
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val myComponentName = ComponentName(context, AppBlockerAccessibilityService::class.java).flattenToString()
            enabledServices.contains(myComponentName)
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

    fun createBatteryOptimizationIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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

            val resultMap = mutableMapOf<String, Int>()

            // 1. Try modern aggregate stats first
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            if (!aggregated.isNullOrEmpty()) {
                for ((pkg, stat) in aggregated) {
                    val minutes = (stat.totalTimeInForeground / 60000L).toInt()
                    if (minutes > 0) {
                        resultMap[pkg] = minutes
                    }
                }
            } else {
                // 2. Fallback to daily bucketed query
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startOfDay,
                    now
                )
                if (stats != null) {
                    val rawMap = mutableMapOf<String, Long>()
                    for (stat in stats) {
                        val totalMs = stat.totalTimeInForeground
                        if (totalMs > 0L) {
                            rawMap[stat.packageName] = (rawMap[stat.packageName] ?: 0L) + totalMs
                        }
                    }
                    for ((pkg, ms) in rawMap) {
                        val minutes = (ms / 60000L).toInt()
                        if (minutes > 0) resultMap[pkg] = minutes
                    }
                }
            }
            resultMap
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun queryCurrentForegroundPackage(): String? {
        if (!hasUsageStatsPermission()) return null
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return null
            val now = System.currentTimeMillis()

            // 1. Check UsageEvents for the most recent ACTIVITY_RESUMED event
            val events = usageStatsManager.queryEvents(now - 15_000L, now)
            var lastForegroundPkg: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForegroundPkg = event.packageName
                }
            }
            if (lastForegroundPkg != null) return lastForegroundPkg

            // 2. Fallback to max lastTimeUsed
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 30_000L,
                now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Queries only real, launchable applications installed on the user's device.
     * Contains zero fake or hardcoded uninstalled demo entries.
     */
    fun discoverCandidateApps(): List<DiscoveredDeviceApp> {
        val pm = context.packageManager
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
                val isReader = isKnownReaderApp(pkg, label)
                installedMap[pkg] = DiscoveredDeviceApp(
                    packageName = pkg,
                    appName = label,
                    category = if (isReader) "Reader" else categorizePackage(pkg, label),
                    isInstalledOnDevice = true,
                    isReaderApp = isReader
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
                    val isReader = isKnownReaderApp(pkg, label)
                    installedMap[pkg] = DiscoveredDeviceApp(
                        packageName = pkg,
                        appName = label,
                        category = if (isReader) "Reader" else categorizePackage(pkg, label),
                        isInstalledOnDevice = true,
                        isReaderApp = isReader
                    )
                }
            }
        } catch (_: Exception) {
        }

        return installedMap.values.sortedWith(
            compareByDescending<DiscoveredDeviceApp> { it.isReaderApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun isKnownReaderApp(packageName: String, label: String): Boolean {
        val lower = "${packageName.lowercase()} ${label.lowercase()}"
        return "reader" in lower || "novel" in lower || "book" in lower ||
                "kindle" in lower || "mihon" in lower || "tachiyomi" in lower ||
                "readera" in lower || "kobo" in lower || "webnovel" in lower ||
                "epub" in lower || "manga" in lower || "aniyomi" in lower ||
                "kotatsu" in lower || "lnreader" in lower || "shosetsu" in lower ||
                "librera" in lower || "fbreader" in lower || "lithium" in lower
    }

    private fun categorizePackage(packageName: String, label: String): String {
        val lower = "${packageName.lowercase()} ${label.lowercase()}"
        return when {
            "youtube" in lower || "netflix" in lower || "video" in lower || "twitch" in lower || "bilibili" in lower -> "Video"
            "instagram" in lower || "twitter" in lower || "facebook" in lower || "threads" in lower || "discord" in lower || "whatsapp" in lower || "telegram" in lower -> "Social"
            "tiktok" in lower || "musical" in lower || "reel" in lower || "shorts" in lower -> "Short Video"
            "reddit" in lower || "news" in lower || "forum" in lower || "quora" in lower -> "Forums"
            "chrome" in lower || "browser" in lower || "firefox" in lower || "brave" in lower || "opera" in lower -> "Browser"
            "game" in lower || "genshin" in lower || "roblox" in lower || "clash" in lower -> "Game"
            else -> "Installed App"
        }
    }
}
