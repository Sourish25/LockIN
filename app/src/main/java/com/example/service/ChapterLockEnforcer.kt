package com.example.service

import android.os.SystemClock
import com.example.data.AppTimerRuleEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class InterceptReason {
    FOCUS_LOCK_BREACH,
    FOCUS_LOCK_LAUNCHER_BREACH,
    DAILY_LIMIT_EXHAUSTED
}

data class InterceptDecision(
    val packageName: String,
    val appName: String,
    val reason: InterceptReason
)

/**
 * Thread-safe, in-memory decision engine shared by the Accessibility Service,
 * Foreground Monitor Service, and UI layer.
 */
object ChapterLockEnforcer {

    const val EXTRA_INTERCEPTED_PACKAGE = "extra_intercepted_package"
    const val EXTRA_INTERCEPT_REASON = "extra_intercept_reason"

    private val isFocusLockActive = AtomicBoolean(false)
    private val pinnedFocusPackage = AtomicReference<String?>(null)
    private val pinnedFocusAppName = AtomicReference("")
    private val focusLockEndTimeMs = AtomicLong(0L)
    private val settingsAllowedUntilMs = AtomicLong(0L)

    private val appRulesMap = ConcurrentHashMap<String, AppTimerRuleEntity>()
    private val temporaryUnlocks = ConcurrentHashMap<String, Long>() // packageName -> unlockUntilEpochMs
    private val knownAppLabels = ConcurrentHashMap<String, String>()

    // Core OS & emergency packages that are ALWAYS permitted (even during Focus Lock)
    private val systemCriticalPackages = setOf(
        "android",
        "com.android.systemui", // Notification shade, status bar, volume bar
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.emergency",
        "com.miui.vpservice"
    )

    // Known default OEM launcher packages
    private val defaultLauncherPackages = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.oneplus.launcher",
        "com.nothing.launcher"
    )

    // Dynamically detected third-party launchers (Nova, Lawnchair, Niagara, etc.)
    private val detectedLauncherPackages = ConcurrentHashMap.newKeySet<String>()

    private val settingsPackages = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.miui.cleanmaster"
    )

    // User-configured Focus Lock exception apps (e.g., GPay, Phone, WhatsApp, Authenticator)
    private val allowedFocusExceptionPackages = ConcurrentHashMap.newKeySet<String>()

    private val lastInterceptTime = AtomicLong(0L)
    private val lastInterceptedPkg = AtomicReference<String?>(null)

    fun registerDetectedLaunchers(launchers: Collection<String>) {
        detectedLauncherPackages.addAll(launchers.map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun isLauncherPackage(packageName: String): Boolean {
        val clean = packageName.trim()
        if (clean.isEmpty()) return false
        if (defaultLauncherPackages.contains(clean) || detectedLauncherPackages.contains(clean)) return true
        return clean.contains(".launcher", ignoreCase = true) ||
                clean.endsWith(".home", ignoreCase = true) ||
                clean.contains("quickstep", ignoreCase = true)
    }

    fun syncFocusExceptions(packages: Collection<String>) {
        allowedFocusExceptionPackages.clear()
        allowedFocusExceptionPackages.addAll(packages.map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun isFocusExceptionPackage(packageName: String): Boolean =
        allowedFocusExceptionPackages.contains(packageName.trim())

    fun getFocusExceptionPackages(): Set<String> =
        allowedFocusExceptionPackages.toSet()

    fun updateFocusLockState(
        active: Boolean,
        pinnedPackage: String?,
        pinnedName: String,
        endTimeMs: Long
    ) {
        isFocusLockActive.set(active)
        pinnedFocusPackage.set(pinnedPackage?.takeIf { it.isNotBlank() })
        pinnedFocusAppName.set(pinnedName)
        focusLockEndTimeMs.set(endTimeMs)
        if (!active) {
            clearThrottle()
        }
    }

    fun allowSystemSettingsTemporarily(durationMs: Long = 90_000L) {
        settingsAllowedUntilMs.set(System.currentTimeMillis() + durationMs)
    }

    fun registerAppLabels(labels: Map<String, String>) {
        knownAppLabels.putAll(labels)
    }

    fun syncRules(rules: List<AppTimerRuleEntity>) {
        val now = System.currentTimeMillis()
        val newMap = rules.associateBy { it.packageName }
        for (rule in rules) {
            knownAppLabels[rule.packageName] = rule.appName
            if (rule.unlockedUntilEpochMs > now) {
                temporaryUnlocks[rule.packageName] = rule.unlockedUntilEpochMs
            }
        }
        appRulesMap.keys.retainAll(newMap.keys)
        appRulesMap.putAll(newMap)
    }

    fun grantTemporaryUnlock(packageName: String, durationMinutes: Int) {
        val until = System.currentTimeMillis() + (durationMinutes * 60_000L)
        temporaryUnlocks[packageName] = until
        clearThrottle()
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
     */
    fun evaluatePackage(packageName: String, myPackageName: String): InterceptDecision? {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return null
        if (cleanPkg == myPackageName) return null

        // 1. Core critical system services (Keyboards, SystemUI notification shade, InCallUI, Emergency)
        if (systemCriticalPackages.contains(cleanPkg)) return null
        if (cleanPkg.contains("inputmethod", ignoreCase = true) ||
            cleanPkg.contains("keyboard", ignoreCase = true) ||
            cleanPkg.contains("incallui", ignoreCase = true)
        ) {
            return null
        }

        val now = System.currentTimeMillis()

        // 2. FOCUS LOCK ACTIVE EVALUATION
        if (isFocusLockActive.get()) {
            val endTime = focusLockEndTimeMs.get()
            if (now < endTime) {
                val allowedFocusPkg = pinnedFocusPackage.get()
                val isAllowedException = allowedFocusExceptionPackages.contains(cleanPkg)

                // If user opened the pinned focus app: ALLOWED
                if (!allowedFocusPkg.isNullOrBlank() && cleanPkg == allowedFocusPkg) {
                    return null
                }

                // If user opened an allowed exception app (e.g. Phone, GPay): ALLOWED
                if (isAllowedException) {
                    return null
                }

                // If user tried to go to the Home Launcher: BREACH! (Triggers instant bounce-back)
                if (isLauncherPackage(cleanPkg)) {
                    return InterceptDecision(
                        packageName = cleanPkg,
                        appName = "Home Screen",
                        reason = InterceptReason.FOCUS_LOCK_LAUNCHER_BREACH
                    )
                }

                // Any other unapproved third-party app: BREACH!
                val appName = appRulesMap[cleanPkg]?.appName
                    ?: knownAppLabels[cleanPkg]
                    ?: formatFallbackPackageName(cleanPkg)
                return InterceptDecision(
                    packageName = cleanPkg,
                    appName = appName,
                    reason = InterceptReason.FOCUS_LOCK_BREACH
                )
            } else {
                isFocusLockActive.set(false)
            }
        }

        // 3. DAILY LIMITS MODE (Focus Lock Inactive)
        // When Focus Lock is NOT running, the home screen is exempt from daily limits
        if (isLauncherPackage(cleanPkg)) {
            return null
        }

        // Settings protection
        if (settingsPackages.contains(cleanPkg) && now < settingsAllowedUntilMs.get()) {
            return null
        }

        // Temporary unlocks
        if (isPackageTemporarilyUnlocked(cleanPkg)) return null

        // Check Daily Screen Time Limit
        val rule = appRulesMap[cleanPkg]
        if (rule != null && rule.isOverDailyBudget) {
            return InterceptDecision(
                packageName = cleanPkg,
                appName = rule.appName,
                reason = InterceptReason.DAILY_LIMIT_EXHAUSTED
            )
        }

        return null
    }

    fun isPinnedPackage(packageName: String): Boolean =
        pinnedFocusPackage.get()?.trim() == packageName.trim()

    fun isPackageAllowedDuringFocus(packageName: String): Boolean {
        val clean = packageName.trim()
        if (!isFocusActive()) return true
        if (systemCriticalPackages.contains(clean)) return true
        if (allowedFocusExceptionPackages.contains(clean)) return true
        return isPinnedPackage(clean)
    }

    fun shouldSuppressMedia(packageName: String): Boolean {
        val clean = packageName.trim()
        if (clean.isBlank()) return false
        if (systemCriticalPackages.contains(clean)) return false
        if (isFocusActive() && !isPackageAllowedDuringFocus(clean)) return true
        val rule = appRulesMap[clean]
        return rule != null && rule.isOverDailyBudget
    }

    /**
     * Short 400ms debounce so rapid window transition events don't spam,
     * while guaranteeing re-interception within 0.4s if the user is still inside the blocked app.
     */
    fun shouldThrottleIntercept(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastTime = lastInterceptTime.get()
        val lastPkg = lastInterceptedPkg.get()

        return if (packageName == lastPkg && (now - lastTime) < 400L) {
            true
        } else {
            lastInterceptTime.set(now)
            lastInterceptedPkg.set(packageName)
            false
        }
    }

    fun clearThrottle() {
        lastInterceptTime.set(0L)
        lastInterceptedPkg.set(null)
    }

    private fun formatFallbackPackageName(packageName: String): String {
        val tail = packageName.substringAfterLast('.')
        return tail.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun isFocusActive(): Boolean =
        isFocusLockActive.get() && System.currentTimeMillis() < focusLockEndTimeMs.get()

    fun isFocusModeCurrentlyActive(): Boolean = isFocusActive()
    fun getFocusEndTimeEpochMillis(): Long = focusLockEndTimeMs.get()
    fun getPinnedAppName(): String = pinnedFocusAppName.get()
    fun getActiveFocusAppName(): String = pinnedFocusAppName.get()
    fun getPinnedAppPackage(): String? = pinnedFocusPackage.get()
    fun getPinnedFocusPackage(): String = pinnedFocusPackage.get().orEmpty()
    fun getWhitelistedReaderPackage(): String = getPinnedFocusPackage()
    fun getCachedRulesSnapshot(): List<AppTimerRuleEntity> = appRulesMap.values.toList()

    fun updateFocusSessionState(
        isRunning: Boolean,
        endTimeEpochMillis: Long,
        selectedFocusPkg: String,
        selectedFocusName: String
    ) {
        updateFocusLockState(
            active = isRunning,
            pinnedPackage = selectedFocusPkg,
            pinnedName = selectedFocusName,
            endTimeMs = endTimeEpochMillis
        )
    }

    fun getFocusRemainingSeconds(): Int {
        val remaining = (focusLockEndTimeMs.get() - System.currentTimeMillis()) / 1000L
        return remaining.coerceAtLeast(0L).toInt()
    }
}
