package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.FocusExceptionAppEntity
import com.example.ui.components.ExpressiveCloverShape
import com.example.ui.components.ExpressiveSealShape
import com.example.ui.components.expressiveGroupedCardShape
import com.example.ui.theme.ExpressiveFeedbackEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.expressiveClickable
import com.example.updater.AppReleaseInfo
import com.example.updater.UpdateState

/**
 * Top-level categories for the nested Material 3 Expressive settings architecture.
 */
enum class SettingsSubCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    PROTECTION_AND_PERMISSIONS(
        title = "Protection & Permissions",
        subtitle = "Accessibility blocker, usage stats, overlay & background battery execution",
        icon = Icons.Filled.Shield
    ),
    FOCUS_AND_BLOCKING(
        title = "Focus & Blocking",
        subtitle = "Session duration presets, allowed exception apps & tactile/sound feedback",
        icon = Icons.Filled.Tune
    ),
    UPDATES_AND_ABOUT(
        title = "Updates & About",
        subtitle = "In-app GitHub release update checker, version v${BuildConfig.VERSION_NAME} & open-source details",
        icon = Icons.Filled.SystemUpdate
    )
}

@Composable
fun ExpressiveSettingsScreen(
    hasUsageStatsPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    isBatteryOptimizedExempt: Boolean,
    isXiaomiDevice: Boolean,
    focusExceptionApps: List<FocusExceptionAppEntity>,
    selectedDurationMinutes: Int,
    updateState: UpdateState,
    isTablet: Boolean,
    onNavigateBackToHome: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenXiaomiAutostartSettings: (() -> Unit)?,
    onOpenExceptionsSheet: () -> Unit,
    onSelectDurationMinutes: (Int) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadRelease: (AppReleaseInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSubCategory by rememberSaveable { mutableStateOf<SettingsSubCategory?>(null) }
    val context = LocalContext.current

    // Handle back button: if in a sub-category, pop back to settings root; if in settings root, return to home
    BackHandler {
        if (activeSubCategory != null) {
            activeSubCategory = null
        } else {
            onNavigateBackToHome()
        }
    }

    AnimatedContent(
        targetState = activeSubCategory,
        transitionSpec = {
            LockInMotion.directionalTabTransition(
                movingForward = targetState != null
            )
        },
        label = "settings_nested_transition",
        modifier = modifier.fillMaxSize()
    ) { currentSubCategory ->
        when (currentSubCategory) {
            null -> {
                SettingsOverviewContent(
                    hasUsageStats = hasUsageStatsPermission,
                    hasAccessibility = hasAccessibilityPermission,
                    hasOverlay = hasOverlayPermission,
                    focusExceptionCount = focusExceptionApps.size,
                    defaultDuration = selectedDurationMinutes,
                    updateState = updateState,
                    isTablet = isTablet,
                    onSelectCategory = { cat ->
                        ExpressiveFeedbackEngine.playPillTap(context)
                        activeSubCategory = cat
                    }
                )
            }

            SettingsSubCategory.PROTECTION_AND_PERMISSIONS -> {
                ProtectionPermissionsContent(
                    hasUsageStats = hasUsageStatsPermission,
                    hasAccessibility = hasAccessibilityPermission,
                    hasOverlay = hasOverlayPermission,
                    hasNotifications = hasNotificationPermission,
                    isBatteryOptimizedExempt = isBatteryOptimizedExempt,
                    isXiaomi = isXiaomiDevice,
                    isTablet = isTablet,
                    onBackClick = {
                        ExpressiveFeedbackEngine.playPillTap(context)
                        activeSubCategory = null
                    },
                    onOpenUsageAccess = onOpenUsageAccessSettings,
                    onOpenAccessibility = onOpenAccessibilitySettings,
                    onOpenOverlay = onOpenOverlaySettings,
                    onOpenAppDetails = onOpenAppDetailsSettings,
                    onOpenNotifications = onOpenNotificationSettings,
                    onOpenBatteryOptimization = onOpenBatteryOptimizationSettings,
                    onOpenXiaomiAutostart = onOpenXiaomiAutostartSettings
                )
            }

            SettingsSubCategory.FOCUS_AND_BLOCKING -> {
                FocusBlockingContent(
                    selectedDurationMinutes = selectedDurationMinutes,
                    focusExceptionApps = focusExceptionApps,
                    isTablet = isTablet,
                    onBackClick = {
                        ExpressiveFeedbackEngine.playPillTap(context)
                        activeSubCategory = null
                    },
                    onSelectDuration = onSelectDurationMinutes,
                    onOpenExceptionsSheet = onOpenExceptionsSheet
                )
            }

            SettingsSubCategory.UPDATES_AND_ABOUT -> {
                UpdatesAndAboutContent(
                    updateState = updateState,
                    isTablet = isTablet,
                    onBackClick = {
                        ExpressiveFeedbackEngine.playPillTap(context)
                        activeSubCategory = null
                    },
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadRelease = onDownloadRelease
                )
            }
        }
    }
}

/**
 * Root Settings View: Presents 3 clean, expressive category entry cards.
 */
@Composable
private fun SettingsOverviewContent(
    hasUsageStats: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    focusExceptionCount: Int,
    defaultDuration: Int,
    updateState: UpdateState,
    isTablet: Boolean,
    onSelectCategory: (SettingsSubCategory) -> Unit
) {
    val allCoreGranted = hasUsageStats && hasAccessibility && hasOverlay

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_overview_screen"),
        contentPadding = PaddingValues(
            horizontal = if (isTablet) 32.dp else 20.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "settings_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure device protection, focus session rules, and app updates",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Category 1: Protection & Permissions
        item(key = "cat_protection") {
            ExpressiveCategoryCard(
                category = SettingsSubCategory.PROTECTION_AND_PERMISSIONS,
                badgeText = if (allCoreGranted) "Protected" else "Action Needed",
                isBadgePositive = allCoreGranted,
                containerColor = if (!allCoreGranted) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                iconContainerColor = if (allCoreGranted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                onClick = { onSelectCategory(SettingsSubCategory.PROTECTION_AND_PERMISSIONS) }
            )
        }

        // Category 2: Focus & Blocking
        item(key = "cat_focus") {
            ExpressiveCategoryCard(
                category = SettingsSubCategory.FOCUS_AND_BLOCKING,
                badgeText = "${defaultDuration}m • $focusExceptionCount exceptions",
                isBadgePositive = true,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onSelectCategory(SettingsSubCategory.FOCUS_AND_BLOCKING) }
            )
        }

        // Category 3: Updates & About
        item(key = "cat_updates") {
            val isUpdateAvailable = updateState is UpdateState.UpdateAvailable
            ExpressiveCategoryCard(
                category = SettingsSubCategory.UPDATES_AND_ABOUT,
                badgeText = if (isUpdateAvailable) "Update Available" else "v${BuildConfig.VERSION_NAME}",
                isBadgePositive = !isUpdateAvailable,
                containerColor = if (isUpdateAvailable) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                iconContainerColor = if (isUpdateAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                onClick = { onSelectCategory(SettingsSubCategory.UPDATES_AND_ABOUT) }
            )
        }

        item(key = "settings_footer") {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LockIN v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Local-First • 100% On-Device Privacy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Sub-Category 1: Protection & Permissions.
 */
@Composable
private fun ProtectionPermissionsContent(
    hasUsageStats: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasNotifications: Boolean,
    isBatteryOptimizedExempt: Boolean,
    isXiaomi: Boolean,
    isTablet: Boolean,
    onBackClick: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenXiaomiAutostart: (() -> Unit)?
) {
    val allCoreGranted = hasUsageStats && hasAccessibility && hasOverlay

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("protection_permissions_sub_screen"),
        contentPadding = PaddingValues(
            horizontal = if (isTablet) 32.dp else 20.dp,
            vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "sub_header_perms") {
            SubCategoryHeader(
                title = "Protection & Permissions",
                subtitle = "Sub-millisecond interception engine and system permissions",
                onBackClick = onBackClick
            )
        }

        // Hero Status Card
        item(key = "perms_hero_status") {
            Surface(
                color = if (allCoreGranted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(ExpressiveSealShape)
                            .background(
                                if (allCoreGranted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allCoreGranted) Icons.Filled.Check else Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (allCoreGranted) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (allCoreGranted) "Interception Engine Active" else "Permissions Setup Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (allCoreGranted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (allCoreGranted) {
                                "All 3 core enforcement permissions are granted. Blocked apps and focus locks trigger without delay."
                            } else {
                                "Grant the missing permissions below to enable real-time app interception."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allCoreGranted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Section: Core Interception Permissions
        item(key = "header_core_perms") {
            Text(
                text = "Core Interception (Required)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "perm_accessibility") {
            ExpressiveSettingItem(
                title = "Accessibility Blocker",
                subtitle = "Enables real-time (<0.02ms) app interception when limits are exceeded",
                isGranted = hasAccessibility,
                icon = Icons.Filled.Shield,
                shape = expressiveGroupedCardShape(index = 0, totalCount = 3),
                onGrantClick = onOpenAccessibility
            )
        }

        item(key = "perm_usage") {
            ExpressiveSettingItem(
                title = "Usage Access",
                subtitle = "Calculates exact daily screen time minutes per application",
                isGranted = hasUsageStats,
                icon = Icons.Filled.HourglassBottom,
                shape = expressiveGroupedCardShape(index = 1, totalCount = 3),
                onGrantClick = onOpenUsageAccess
            )
        }

        item(key = "perm_overlay") {
            ExpressiveSettingItem(
                title = "Display Over Other Apps",
                subtitle = "Surfaces the focus shield and cognitive friction puzzles over blocked applications",
                isGranted = hasOverlay,
                icon = Icons.Filled.Security,
                shape = expressiveGroupedCardShape(index = 2, totalCount = 3),
                onGrantClick = onOpenOverlay
            )
        }

        // Section: Android 13+ Restricted Settings Helper
        item(key = "helper_restricted_settings") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restricted Settings (Android 13+)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "If Accessibility is greyed out: tap App Info -> top-right ⋮ menu -> Allow restricted settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenAppDetails,
                        shape = CircleShape
                    ) {
                        Text("App Info", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Section: Background Longevity & Battery
        item(key = "header_background_battery") {
            Text(
                text = "Background Execution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        item(key = "perm_battery") {
            ExpressiveSettingItem(
                title = "Unrestricted Battery",
                subtitle = "Prevents OS from killing background monitoring (or select App Info -> Battery -> Unrestricted)",
                isGranted = isBatteryOptimizedExempt,
                icon = Icons.Outlined.BatteryChargingFull,
                shape = RoundedCornerShape(24.dp),
                onGrantClick = onOpenBatteryOptimization
            )
        }

        if (isXiaomi && onOpenXiaomiAutostart != null) {
            item(key = "perm_xiaomi_autostart") {
                ExpressiveSettingItem(
                    title = "HyperOS / MIUI Autostart",
                    subtitle = "Allows LockIN to auto-resume after device reboot on Xiaomi devices",
                    isGranted = false,
                    icon = Icons.Outlined.Shield,
                    shape = RoundedCornerShape(24.dp),
                    actionLabel = "Configure",
                    onGrantClick = onOpenXiaomiAutostart
                )
            }
        }

        item(key = "perm_notifications") {
            ExpressiveSettingItem(
                title = "Timer & Limit Notifications",
                subtitle = "Enables live Focus Lock chronometer notifications and 5m remaining alerts",
                isGranted = hasNotifications,
                icon = Icons.Filled.Notifications,
                shape = RoundedCornerShape(24.dp),
                onGrantClick = onOpenNotifications
            )
        }
    }
}

/**
 * Sub-Category 2: Focus & Blocking Preferences.
 */
@Composable
private fun FocusBlockingContent(
    selectedDurationMinutes: Int,
    focusExceptionApps: List<FocusExceptionAppEntity>,
    isTablet: Boolean,
    onBackClick: () -> Unit,
    onSelectDuration: (Int) -> Unit,
    onOpenExceptionsSheet: () -> Unit
) {
    val durations = listOf(15, 25, 30, 45, 60, 90, 120)
    var soundEnabled by remember { mutableStateOf(ExpressiveFeedbackEngine.isSoundEnabled) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("focus_blocking_sub_screen"),
        contentPadding = PaddingValues(
            horizontal = if (isTablet) 32.dp else 20.dp,
            vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "sub_header_focus") {
            SubCategoryHeader(
                title = "Focus & Blocking",
                subtitle = "Session durations, allowed exception apps & tactile feedback",
                onBackClick = onBackClick
            )
        }

        // Section: Default Focus Duration Presets
        item(key = "focus_duration_presets") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Default Focus Lock Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose the default session duration for the fast-scrolling slot clock on the Focus Lock screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(durations) { mins ->
                            val isSelected = selectedDurationMinutes == mins
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                label = "DurationPillBg"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "DurationPillText"
                            )

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable {
                                        ExpressiveFeedbackEngine.playSlotTick(context)
                                        onSelectDuration(mins)
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Allowed Exception Apps
        item(key = "focus_exceptions_card") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(ExpressiveCloverShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allowed Exception Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${focusExceptionApps.size} apps whitelisted (e.g. Phone, Maps). Accessible during active Focus Lock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onOpenExceptionsSheet,
                        shape = CircleShape
                    ) {
                        Text("Manage", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Section: Expressive Haptics & Sound
        item(key = "header_haptics_sound") {
            Text(
                text = "Tactile & Audio Feedback",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "toggle_micro_sound") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Acoustic Micro-Sounds",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Synthesized zero-latency clicks for dials and lock-in chords",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { isChecked ->
                            soundEnabled = isChecked
                            ExpressiveFeedbackEngine.isSoundEnabled = isChecked
                            if (isChecked) {
                                ExpressiveFeedbackEngine.playPillTap(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

/**
 * Sub-Category 3: In-App Updates & About.
 */
@Composable
private fun UpdatesAndAboutContent(
    updateState: UpdateState,
    isTablet: Boolean,
    onBackClick: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadRelease: (AppReleaseInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val isChecking = updateState is UpdateState.Checking
    val isUpdateAvailable = updateState is UpdateState.UpdateAvailable

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("updates_about_sub_screen"),
        contentPadding = PaddingValues(
            horizontal = if (isTablet) 32.dp else 20.dp,
            vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "sub_header_updates") {
            SubCategoryHeader(
                title = "Updates & About",
                subtitle = "In-app updates via GitHub releases and project information",
                onBackClick = onBackClick
            )
        }

        // Section: In-App GitHub Releases Updater
        item(key = "updater_card") {
            Surface(
                color = if (isUpdateAvailable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LockIN v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Release Channel: GitHub Releases (Sourish25/LockIN)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(ExpressiveSealShape)
                                .background(
                                    if (isUpdateAvailable) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Status Text
                    Text(
                        text = when (updateState) {
                            is UpdateState.Checking -> "Checking GitHub releases repository for new APK builds..."
                            is UpdateState.UpdateAvailable -> {
                                if (updateState.release.isSameVersionRebuild) {
                                    "New build for ${updateState.release.tagName} is available with recent improvements!"
                                } else {
                                    "New version ${updateState.release.tagName} is available with improvements and fixes!"
                                }
                            }
                            is UpdateState.UpToDate -> "You are on the latest official build (v${BuildConfig.VERSION_NAME}). All features up to date."
                            is UpdateState.Error -> "Update check error: ${updateState.message}"
                            else -> "Stay up to date with multi-ABI releases directly from GitHub."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onCheckForUpdates,
                        enabled = !isChecking,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUpdateAvailable) "View & Install Update" else "Check for Updates",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (updateState is UpdateState.UpToDate && updateState.latestRelease != null) {
                        OutlinedButton(
                            onClick = { onDownloadRelease(updateState.latestRelease) },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reinstall Latest Build (${updateState.latestRelease.tagName})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Section: About & Community
        item(key = "header_about_links") {
            Text(
                text = "About LockIN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "about_repo_link") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = expressiveGroupedCardShape(index = 0, totalCount = 2),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Sourish25/LockIN")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "GitHub Repository",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "https://github.com/Sourish25/LockIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Repository",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item(key = "about_license") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = expressiveGroupedCardShape(index = 1, totalCount = 2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Open Source License",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "MIT License • Free and open source software",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Header with a back arrow button for sub-category views.
 */
@Composable
private fun SubCategoryHeader(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 50.dp)
        )
    }
}

/**
 * Reusable category card for the root settings overview screen.
 */
@Composable
private fun ExpressiveCategoryCard(
    category: SettingsSubCategory,
    badgeText: String,
    isBadgePositive: Boolean,
    containerColor: Color,
    iconContainerColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(ExpressiveSealShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = if (isBadgePositive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isBadgePositive) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.errorContainer
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isBadgePositive) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${category.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Reusable row item for permission and toggle settings.
 */
@Composable
private fun ExpressiveSettingItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    icon: ImageVector,
    shape: RoundedCornerShape,
    actionLabel: String = "Grant",
    onGrantClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
