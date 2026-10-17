package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
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
import com.example.data.RealAppIcon
import com.example.data.formatMinutesClean
import com.example.ui.components.ExpressiveSealShape
import com.example.ui.components.expressiveGroupedCardShape
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AppVaultScreen(
    appRules: List<AppTimerRuleEntity>,
    isFocusSessionActive: Boolean,
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
    onOpenAddAppSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val blockedRules = appRules.filter { it.isCurrentlyBlocked(isFocusSessionActive) }
    val activeRules = appRules.filterNot { it.isCurrentlyBlocked(isFocusSessionActive) }
    val totalUsedMinutesToday = appRules.sumOf { it.usedMinutesToday }

    if (isTablet) {
        // --- TABLET 2-PANE ADAPTIVE LAYOUT (Optimized for Xiaomi Pad 7 3:2 ratio) ---
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                .testTag("app_vault_screen"),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Pane: Hero Bento + Live Permission Status
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
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    text = if (blockedRules.isNotEmpty()) "RESTRICTIONS ACTIVE" else "DAILY SCREEN TIME GUARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            if (totalUsedMinutesToday > 0) {
                                Text(
                                    text = "${formatMinutesClean(totalUsedMinutesToday)} today",
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
                            Text(
                                text = "$count",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 52.sp,
                                lineHeight = 52.sp,
                                letterSpacing = (-2.0).sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                                .height(48.dp)
                                .testTag("open_add_app_sheet_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add App Limit", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Live Android Permissions Status Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SYSTEM ENFORCEMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PermissionStatusItem(
                            title = "Usage Access",
                            subtitle = "Tracks daily screen time",
                            isGranted = hasUsageStatsPermission,
                            onClick = onOpenUsageAccessSettings
                        )

                        PermissionStatusItem(
                            title = "Accessibility Blocker",
                            subtitle = "Instant window interceptor",
                            isGranted = hasAccessibilityPermission,
                            onClick = onOpenAccessibilitySettings
                        )

                        PermissionStatusItem(
                            title = "Overlay Shield",
                            subtitle = "Displays puzzle barrier",
                            isGranted = hasOverlayPermission,
                            onClick = onOpenOverlaySettings
                        )

                        FilledTonalButton(
                            onClick = onOpenPermissionsSheet,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All Shield Settings", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Right Pane: Grouped Expressive App List aligned flush with Left Pane
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
                                text = "TRACKED APPLICATIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${activeRules.size} ACTIVE • ${blockedRules.size} LOCKED",
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
                            onTapCard = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTapAppRule(rule)
                            },
                            onTapUnlock = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRequestUnlockApp(rule)
                            }
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
                            onTapCard = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTapAppRule(rule)
                            }
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
                        .padding(bottom = 8.dp),
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
                            Surface(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (blockedRules.isNotEmpty()) "LIMIT REACHED" else "DAILY GUARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 52.sp,
                                    lineHeight = 52.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (blockedRules.isNotEmpty()) {
                                        if (count == 1) "app\nblocked" else "apps\nblocked"
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
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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

            // Real Permissions Setup Pill
            item {
                Surface(
                    color = if (!hasUsageStatsPermission || !hasAccessibilityPermission) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onOpenPermissionsSheet() }
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (!hasUsageStatsPermission || !hasAccessibilityPermission) Icons.Filled.Shield
                                else Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (!hasUsageStatsPermission || !hasAccessibilityPermission) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = if (!hasAccessibilityPermission) "Accessibility Interceptor Required"
                                    else if (!hasUsageStatsPermission) "Usage Access Required"
                                    else "System Shield Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (!hasAccessibilityPermission || !hasUsageStatsPermission) "Tap to grant permissions for reliable blocking"
                                    else "All permissions & background guards verified",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onOpenPermissionsSheet,
                            shape = CircleShape,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = if (!hasUsageStatsPermission || !hasAccessibilityPermission) "Setup" else "Shield",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
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
                        onTapCard = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTapAppRule(rule)
                        },
                        onTapUnlock = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRequestUnlockApp(rule)
                        }
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
                        onTapCard = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTapAppRule(rule)
                        }
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
private fun PermissionStatusItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
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
            if (isGranted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Grant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveBlockedAppCard(
    rule: AppTimerRuleEntity,
    shape: RoundedCornerShape,
    onTapCard: () -> Unit,
    onTapUnlock: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onTapCard() }
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
                    .clickable { onTapUnlock() }
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
    onTapCard: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = rule.usageProgress.coerceIn(0.06f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SplitBarProgress"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onTapCard() }
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
