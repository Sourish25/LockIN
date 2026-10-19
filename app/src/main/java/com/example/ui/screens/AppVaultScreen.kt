package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppTimerRuleEntity
import com.example.data.DiscoveredDeviceApp
import com.example.data.RealAppIcon
import com.example.data.formatMinutesClean
import com.example.ui.components.ExpressiveSealShape
import com.example.ui.components.expressiveGroupedCardShape
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.expressiveClickable

@Composable
fun AppVaultScreen(
    appRules: List<AppTimerRuleEntity>,
    discoveredApps: List<DiscoveredDeviceApp> = emptyList(),
    isFocusSessionActive: Boolean,
    activeFocusAppName: String = "",
    activeFocusPackageName: String = "",
    activeFocusCountdown: String = "",
    hasUsageStatsPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasOverlayPermission: Boolean = false,
    isTablet: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit = {},
    onOpenPermissionsSheet: () -> Unit,
    onTapAppRule: (AppTimerRuleEntity) -> Unit,
    onRequestUnlockApp: (AppTimerRuleEntity) -> Unit,
    onQuickAddAppLimit: (DiscoveredDeviceApp, Int) -> Unit = { _, _ -> },
    onOpenAddAppSheet: () -> Unit,
    onLaunchPinnedFocusApp: () -> Unit = {},
    onExtendFocusLock: () -> Unit = {},
    onSwitchToFocusTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val blockedRules = appRules.filter { it.isCurrentlyBlocked(isFocusSessionActive) }
    val activeRules = appRules.filterNot { it.isCurrentlyBlocked(isFocusSessionActive) }
    val totalUsedMinutesToday = appRules.sumOf { it.usedMinutesToday }
    val totalBudgetMinutesToday = appRules.sumOf { it.dailyLimitMinutes }

    val allCorePermissionsGranted =
        hasUsageStatsPermission && hasAccessibilityPermission && hasOverlayPermission

    val quickLimitCandidates = remember(discoveredApps, appRules) {
        val existingPkgs = appRules.map { it.packageName }.toSet()
        discoveredApps
            .filter { it.packageName !in existingPkgs && !it.isReaderApp }
            .sortedWith(
                compareByDescending<DiscoveredDeviceApp> { it.usedMinutesToday }
                    .thenByDescending { it.category != "Installed App" }
                    .thenBy { it.appName.lowercase() }
            )
            .take(3)
    }

    if (isTablet) {
        // --- TABLET 2-PANE ADAPTIVE LAYOUT ---
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                .testTag("app_vault_screen"),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Pane: Hero Daily Budget Card + Active Focus Banner + Missing Permissions + Quick Add Used Apps
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Bento Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (blockedRules.isNotEmpty()) "LIMITS ENFORCED" else "DAILY TIME BUDGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            if (totalBudgetMinutesToday > 0) {
                                Text(
                                    text = "${formatMinutesClean(totalUsedMinutesToday)} / ${formatMinutesClean(totalBudgetMinutesToday)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        val count = if (blockedRules.isNotEmpty()) blockedRules.size else appRules.size
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedContent(
                                targetState = count,
                                transitionSpec = { LockInMotion.numericCounterTransition() },
                                label = "tablet_hero_count"
                            ) { targetCount ->
                                Text(
                                    text = "$targetCount",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 54.sp,
                                    lineHeight = 54.sp,
                                    letterSpacing = (-2.0).sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = if (blockedRules.isNotEmpty()) {
                                    if (count == 1) "app locked\nfor today" else "apps locked\nfor today"
                                } else {
                                    if (count == 1) "app with\ndaily timer" else "apps with\ndaily timers"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        if (totalBudgetMinutesToday > 0) {
                            val rawFraction = (totalUsedMinutesToday.toFloat() / totalBudgetMinutesToday.toFloat())
                                .coerceIn(0.06f, 1f)
                            val totalFraction by animateFloatAsState(
                                targetValue = rawFraction,
                                animationSpec = LockInMotion.spatialSpring(),
                                label = "tablet_budget_fraction"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(totalFraction)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                                val rem = (1f - totalFraction).coerceAtLeast(0.04f)
                                Box(
                                    modifier = Modifier
                                        .weight(rem)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                )
                            }
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenAddAppSheet()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("open_add_app_sheet_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add App Limit", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Connected Live Focus Lock Banner (when Focus Lock is active)
                AnimatedVisibility(
                    visible = isFocusSessionActive,
                    enter = fadeIn() + expandVertically(animationSpec = LockInMotion.spatialSpring()),
                    exit = fadeOut() + shrinkVertically(animationSpec = LockInMotion.spatialSpring())
                ) {
                    ConnectedFocusSessionBanner(
                        appName = activeFocusAppName.ifBlank { "Focus App" },
                        packageName = activeFocusPackageName,
                        countdownText = activeFocusCountdown,
                        onLaunchApp = onLaunchPinnedFocusApp,
                        onExtend15m = onExtendFocusLock,
                        onOpenFocusTab = onSwitchToFocusTab
                    )
                }

                // Missing Permissions Setup Card — ONLY visible when at least 1 core permission is missing!
                AnimatedVisibility(
                    visible = !allCorePermissionsGranted,
                    enter = fadeIn() + expandVertically(animationSpec = LockInMotion.spatialSpring()),
                    exit = fadeOut() + shrinkVertically(animationSpec = LockInMotion.spatialSpring())
                ) {
                    MissingPermissionsCard(
                        hasUsageStatsPermission = hasUsageStatsPermission,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        hasOverlayPermission = hasOverlayPermission,
                        onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onOpenPermissionsSheet = onOpenPermissionsSheet
                    )
                }

                // Functional Quick-Limit Card for Uncapped Installed Apps
                if (quickLimitCandidates.isNotEmpty()) {
                    QuickLimitSuggestionsCard(
                        candidates = quickLimitCandidates,
                        onQuickAdd = { app, mins ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onQuickAddAppLimit(app, mins)
                        }
                    )
                }
            }

            // Right Pane: Grouped Expressive App List
            LazyColumn(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONFIGURED LIMITS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${activeRules.size} WITHIN LIMIT • ${blockedRules.size} LOCKED",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (blockedRules.isNotEmpty()) {
                    item {
                        Text(
                            text = "LOCKED FOR TODAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                    itemsIndexed(blockedRules, key = { _, r -> "b_${r.packageName}" }) { index, rule ->
                        ExpressiveBlockedAppCard(
                            rule = rule,
                            shape = expressiveGroupedCardShape(index, blockedRules.size, 30.dp, 10.dp),
                            onTapCard = { onTapAppRule(rule) },
                            onTapUnlock = { onRequestUnlockApp(rule) },
                            modifier = Modifier.animateItem(
                                placementSpec = LockInMotion.spatialSpring()
                            )
                        )
                    }
                }

                if (activeRules.isNotEmpty()) {
                    item {
                        Text(
                            text = "ACTIVE DAILY LIMITS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }
                    itemsIndexed(activeRules, key = { _, r -> "a_${r.packageName}" }) { index, rule ->
                        ExpressiveActiveAppCard(
                            rule = rule,
                            shape = expressiveGroupedCardShape(index, activeRules.size, 30.dp, 10.dp),
                            onTapCard = { onTapAppRule(rule) },
                            modifier = Modifier.animateItem(
                                placementSpec = LockInMotion.spatialSpring()
                            )
                        )
                    }
                }

                if (blockedRules.isEmpty() && activeRules.isEmpty()) {
                    item {
                        EmptyAppVaultCard(onOpenAddAppSheet = onOpenAddAppSheet)
                    }
                }
            }
        }
    } else {
        // --- PHONE SINGLE COLUMN ADAPTIVE LAYOUT ---
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("app_vault_screen"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Asymmetric Split Bento Header
            item {
                val count = if (blockedRules.isNotEmpty()) blockedRules.size else appRules.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(
                            topStart = 36.dp,
                            bottomStart = 36.dp,
                            topEnd = 12.dp,
                            bottomEnd = 12.dp
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = if (blockedRules.isNotEmpty()) "LIMIT REACHED" else "DAILY BUDGET",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }

                                if (totalUsedMinutesToday > 0) {
                                    Text(
                                        text = "${formatMinutesClean(totalUsedMinutesToday)} used",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimatedContent(
                                    targetState = count,
                                    transitionSpec = { LockInMotion.numericCounterTransition() },
                                    label = "phone_hero_count"
                                ) { targetCount ->
                                    Text(
                                        text = "$targetCount",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 52.sp,
                                        lineHeight = 52.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = if (blockedRules.isNotEmpty()) {
                                        if (count == 1) "app\nlocked" else "apps\nlocked"
                                    } else {
                                        if (count == 1) "active\nlimit" else "active\nlimits"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 36.dp,
                            bottomEnd = 36.dp
                        ),
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    bottomStart = 12.dp,
                                    topEnd = 36.dp,
                                    bottomEnd = 36.dp
                                )
                            )
                            .expressiveClickable(pressedScale = 0.96f) {
                                onOpenAddAppSheet()
                            }
                            .testTag("open_add_app_sheet_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(ExpressiveSealShape)
                                    .background(MaterialTheme.colorScheme.onTertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Limit",
                                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "New\nLimit",
                                style = MaterialTheme.typography.headlineMedium,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Connected Live Focus Lock Banner (when Focus Lock is active)
            if (isFocusSessionActive) {
                item {
                    ConnectedFocusSessionBanner(
                        appName = activeFocusAppName.ifBlank { "Focus App" },
                        packageName = activeFocusPackageName,
                        countdownText = activeFocusCountdown,
                        onLaunchApp = onLaunchPinnedFocusApp,
                        onExtend15m = onExtendFocusLock,
                        onOpenFocusTab = onSwitchToFocusTab
                    )
                }
            }

            // Missing Permissions Banner — ONLY shown when at least 1 permission is not yet granted!
            if (!allCorePermissionsGranted) {
                item {
                    MissingPermissionsCard(
                        hasUsageStatsPermission = hasUsageStatsPermission,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        hasOverlayPermission = hasOverlayPermission,
                        onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onOpenPermissionsSheet = onOpenPermissionsSheet
                    )
                }
            }

            // Blocked Apps
            if (blockedRules.isNotEmpty()) {
                item {
                    Text(
                        text = "LOCKED FOR TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(blockedRules, key = { _, r -> "b_${r.packageName}" }) { index, rule ->
                    ExpressiveBlockedAppCard(
                        rule = rule,
                        shape = expressiveGroupedCardShape(index, blockedRules.size, 32.dp, 10.dp),
                        onTapCard = { onTapAppRule(rule) },
                        onTapUnlock = { onRequestUnlockApp(rule) },
                        modifier = Modifier.animateItem(
                            placementSpec = LockInMotion.spatialSpring()
                        )
                    )
                }
            }

            // Active Limits
            if (activeRules.isNotEmpty()) {
                item {
                    Text(
                        text = "ACTIVE LIMITS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(activeRules, key = { _, r -> "a_${r.packageName}" }) { index, rule ->
                    ExpressiveActiveAppCard(
                        rule = rule,
                        shape = expressiveGroupedCardShape(index, activeRules.size, 32.dp, 10.dp),
                        onTapCard = { onTapAppRule(rule) },
                        modifier = Modifier.animateItem(
                            placementSpec = LockInMotion.spatialSpring()
                        )
                    )
                }
            }

            if (blockedRules.isEmpty() && activeRules.isEmpty()) {
                item {
                    EmptyAppVaultCard(onOpenAddAppSheet = onOpenAddAppSheet)
                }
            }

            if (quickLimitCandidates.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    QuickLimitSuggestionsCard(
                        candidates = quickLimitCandidates,
                        onQuickAdd = { app, mins ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onQuickAddAppLimit(app, mins)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Deeply connected live Focus Lock banner displayed on the Home screen whenever a
 * Single-App Focus Lock session is active, allowing 1-tap launch or +15m extension.
 */
@Composable
private fun ConnectedFocusSessionBanner(
    appName: String,
    packageName: String,
    countdownText: String,
    onLaunchApp: () -> Unit,
    onExtend15m: () -> Unit,
    onOpenFocusTab: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .expressiveClickable { onOpenFocusTab() }
            .testTag("connected_focus_session_banner")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        RealAppIcon(
                            packageName = packageName,
                            appName = appName,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOCUS LOCK ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (countdownText.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = countdownText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLaunchApp,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Text(
                        text = "Open $appName",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onExtend15m,
                    shape = CircleShape,
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        text = "+15m",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Only displays permissions that have NOT been granted yet.
 * Once a permission is granted, its row disappears immediately.
 * Once all 3 are granted, this entire card disappears from the Home screen.
 */
@Composable
private fun MissingPermissionsCard(
    hasUsageStatsPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasOverlayPermission: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenPermissionsSheet: () -> Unit
) {
    val missingCount = listOf(
        hasUsageStatsPermission,
        hasAccessibilityPermission,
        hasOverlayPermission
    ).count { !it }

    if (missingCount == 0) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("missing_permissions_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (missingCount == 1) "1 PERMISSION NEEDED"
                        else "$missingCount PERMISSIONS NEEDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "All Settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .expressiveClickable { onOpenPermissionsSheet() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (!hasAccessibilityPermission) {
                MissingPermissionActionRow(
                    title = "Accessibility Blocker",
                    subtitle = "Required to intercept apps when switching",
                    onClick = onOpenAccessibilitySettings
                )
            }

            if (!hasUsageStatsPermission) {
                MissingPermissionActionRow(
                    title = "Usage Access",
                    subtitle = "Required to track YouTube & app minutes",
                    onClick = onOpenUsageAccessSettings
                )
            }

            if (!hasOverlayPermission) {
                MissingPermissionActionRow(
                    title = "Display Over Other Apps",
                    subtitle = "Required to show lock barrier over apps",
                    onClick = onOpenOverlaySettings
                )
            }
        }
    }
}

@Composable
private fun MissingPermissionActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickLimitSuggestionsCard(
    candidates: List<DiscoveredDeviceApp>,
    onQuickAdd: (DiscoveredDeviceApp, Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_limit_suggestions_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUICK ADD LIMIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "1-TAP 30M CAP",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            candidates.forEach { app ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .expressiveClickable { onQuickAdd(app, 30) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                RealAppIcon(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (app.usedMinutesToday > 0) {
                                        "${formatMinutesClean(app.usedMinutesToday)} used today"
                                    } else {
                                        app.category
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "+ 30m",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAppVaultCard(
    onOpenAddAppSheet: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("empty_app_vault_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(ExpressiveSealShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = "No Daily App Limits Configured",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Select installed distraction apps from your device and set a daily screen time cap. Once an app reaches its limit, LockIN blocks access behind a cognitive puzzle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            FilledTonalButton(
                onClick = onOpenAddAppSheet,
                shape = CircleShape,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Choose Installed App",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ExpressiveBlockedAppCard(
    rule: AppTimerRuleEntity,
    shape: RoundedCornerShape,
    onTapCard: () -> Unit,
    onTapUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .expressiveClickable { onTapCard() }
            .testTag("app_timer_card_${rule.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    RealAppIcon(
                        packageName = rule.packageName,
                        appName = rule.appName,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = rule.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatMinutesClean(rule.usedMinutesToday)} / ${formatMinutesClean(rule.dailyLimitMinutes)} limit reached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.onErrorContainer,
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .expressiveClickable { onTapUnlock() }
                    .testTag("unlock_app_button_${rule.packageName}")
            ) {
                Text(
                    text = "Puzzle Lock",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpressiveActiveAppCard(
    rule: AppTimerRuleEntity,
    shape: RoundedCornerShape,
    onTapCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = rule.usageProgress.coerceIn(0.06f, 1f),
        animationSpec = LockInMotion.spatialSpring(),
        label = "SplitBarProgress"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .expressiveClickable { onTapCard() }
            .testTag("app_timer_card_${rule.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        RealAppIcon(
                            packageName = rule.packageName,
                            appName = rule.appName,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = rule.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (rule.isTemporarilyUnlocked()) "10m pass active"
                            else "${formatMinutesClean(rule.usedMinutesToday)} used • ${formatMinutesClean(rule.remainingMinutesToday)} left today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (rule.isTemporarilyUnlocked()) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = formatMinutesClean(rule.dailyLimitMinutes),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // M3 Expressive Split-Track Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(animatedProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                val remainingWeight = (1f - animatedProgress).coerceAtLeast(0.04f)
                Box(
                    modifier = Modifier
                        .weight(remainingWeight)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
    }
}
