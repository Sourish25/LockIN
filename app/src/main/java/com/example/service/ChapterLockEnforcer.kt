package com.example.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.AppTimerRuleEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class InterceptReason {
    FOCUS_LOCK_BREACH,
    DAILY_LIMIT_EXHAUSTED
}

data class InterceptDecision(
    val packageName: String,
    val appName: String,
    val reason: InterceptReason
)

/**
 * High-performance, thread-safe in-memory decision engine for the Accessibility Service
 * and Foreground Service. Executes package lookups in <0.02ms without touching disk or DB.
 */
object ChapterLockEnforcer {

    const val EXTRA_INTERCEPTED_PACKAGE = "extra_intercepted_package"
    const val EXTRA_INTERCEPT_REASON = "extra_intercept_reason"

    private val isFocusLockActive = AtomicBoolean(false)
    private val pinnedFocusPackage = AtomicReference<String?>(null)
    private val pinnedFocusAppName = AtomicReference("")
    private val focusLockEndTimeMs = AtomicLong(0L)

    private val blockedPackages = ConcurrentHashMap<String, String>() // packageName -> appName
    private val appRulesMap = ConcurrentHashMap<String, AppTimerRuleEntity>()
    private val temporaryUnlocks = ConcurrentHashMap<String, Long>() // packageName -> unlockUntilEpochMs

    // System packages that must never be blocked to prevent device soft-lock or launcher crash
    private val whitelistedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.miui.home", // Xiaomi / HyperOS Launcher
        "com.miui.securitycenter",
        "com.miui.cleanmaster",
        "com.sec.android.app.launcher", // Samsung OneUI Launcher
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller"
    )

    private val lastInterceptTime = AtomicLong(0L)
    private val lastInterceptedPkg = AtomicReference<String?>(null)

    fun updateFocusLockState(
        active: Boolean,
        pinnedPackage: String?,
        pinnedName: String,
        endTimeMs: Long
    ) {
        isFocusLockActive.set(active)
        pinnedFocusPackage.set(pinnedPackage)
        pinnedFocusAppName.set(pinnedName)
        focusLockEndTimeMs.set(endTimeMs)
    }

    fun syncRules(rules: List<AppTimerRuleEntity>) {
        appRulesMap.clear()
        val now = System.currentTimeMillis()
        val newBlocked = mutableMapOf<String, String>()
        for (rule in rules) {
            appRulesMap[rule.packageName] = rule
            if (rule.unlockedUntilEpochMs > now) {
                temporaryUnlocks[rule.packageName] = rule.unlockedUntilEpochMs
            }
            if (rule.isCurrentlyBlocked(isReadingSessionActive = false, nowMs = now)) {
                newBlocked[rule.packageName] = rule.appName
            }
        }
        blockedPackages.clear()
        blockedPackages.putAll(newBlocked)
    }

    fun grantTemporaryUnlock(packageName: String, durationMinutes: Int) {
        val until = System.currentTimeMillis() + (durationMinutes * 60_000L)
        temporaryUnlocks[packageName] = until
        blockedPackages.remove(packageName)
    }

    fun isPackageTemporarilyUnlocked(packageName: String): Boolean {
        val until = temporaryUnlocks[packageName] ?: return false
        return if (until > System.currentTimeMillis()) {
            true
        } else {
            temporaryUnlocks.remove(packageName)
            false
        }
    }

    /**
     * Determines whether the given foreground package should be intercepted.
     * Evaluated within microseconds on the Accessibility event thread.
     */
    fun evaluatePackage(packageName: String, myPackageName: String): InterceptDecision? {
        if (packageName.isBlank()) return null
        if (packageName == myPackageName) return null
        if (whitelistedPackages.contains(packageName)) return null
        if (isPackageTemporarilyUnlocked(packageName)) return null

        val now = System.currentTimeMillis()

        // 1. Check Focus Lock
        if (isFocusLockActive.get()) {
            val endTime = focusLockEndTimeMs.get()
            if (now < endTime) {
                val allowedReaderPkg = pinnedFocusPackage.get()
                // If it's not the pinned reader app, user is attempting to leave the reader!
                if (allowedReaderPkg != null && packageName != allowedReaderPkg) {
                    val appName = appRulesMap[packageName]?.appName ?: packageName
                    return InterceptDecision(
                        packageName = packageName,
                        appName = appName,
                        reason = InterceptReason.FOCUS_LOCK_BREACH
                    )
                }
            } else {
                // Focus lock expired
                isFocusLockActive.set(false)
            }
        }

        // 2. Check Daily Limit
        val rule = appRulesMap[packageName]
        if (rule != null && rule.isOverDailyBudget) {
            return InterceptDecision(
                packageName = packageName,
                appName = rule.appName,
                reason = InterceptReason.DAILY_LIMIT_EXHAUSTED
            )
        }

        return null
    }

    /**
     * Debounce rapid multiple events for the same package within 1.5 seconds
     */
    fun shouldThrottleIntercept(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastTime = lastInterceptTime.get()
        val lastPkg = lastInterceptedPkg.get()

        return if (packageName == lastPkg && (now - lastTime) < 1500L) {
            true
        } else {
            lastInterceptTime.set(now)
            lastInterceptedPkg.set(packageName)
            false
        }
    }

    fun isFocusActive(): Boolean = isFocusLockActive.get() && System.currentTimeMillis() < focusLockEndTimeMs.get()
    fun getPinnedAppName(): String = pinnedFocusAppName.get()
    fun getPinnedAppPackage(): String? = pinnedFocusPackage.get()
    fun getFocusRemainingSeconds(): Int {
        val remaining = (focusLockEndTimeMs.get() - System.currentTimeMillis()) / 1000L
        return remaining.coerceAtLeast(0L).toInt()
    }
}
