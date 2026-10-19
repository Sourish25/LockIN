package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppTimerRuleEntity
import com.example.data.DiscoveredDeviceApp
import com.example.data.PinnedFocusAppEntity
import com.example.data.RealAppIcon
import com.example.data.formatMinutesClean
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.expressiveClickable

private val DAILY_LIMIT_PRESETS = listOf(1, 5, 10, 15, 30, 45, 60, 90, 120, 180)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditAppLimitBottomSheet(
    rule: AppTimerRuleEntity?,
    onDismiss: () -> Unit,
    onSaveLimit: (Int) -> Unit,
    onRemoveRule: () -> Unit
) {
    if (rule == null) return

    var selectedMinutes by remember(rule.dailyLimitMinutes) {
        mutableIntStateOf(rule.dailyLimitMinutes.coerceAtLeast(1))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        RealAppIcon(
                            packageName = rule.packageName,
                            appName = rule.appName,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Column {
                        Text(
                            text = rule.appName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatMinutesClean(rule.usedMinutesToday)} used today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    AnimatedContent(
                        targetState = selectedMinutes,
                        transitionSpec = { LockInMotion.numericCounterTransition() },
                        label = "edit_limit_badge"
                    ) { mins ->
                        Text(
                            text = formatMinutesClean(mins),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            FineTuneMinutesRow(
                selectedMinutes = selectedMinutes,
                usedMinutesToday = rule.usedMinutesToday,
                onAdjust = { newMins -> selectedMinutes = newMins.coerceIn(1, 360) }
            )

            Text(
                text = "Daily time limit presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DAILY_LIMIT_PRESETS.forEach { mins ->
                    val chosen = selectedMinutes == mins
                    Surface(
                        color = if (chosen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .expressiveClickable { selectedMinutes = mins }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatMinutesClean(mins),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (chosen) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onRemoveRule,
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.height(58.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove limit"
                    )
                }

                Button(
                    onClick = { onSaveLimit(selectedMinutes) },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("save_app_limit_button")
                ) {
                    Text(
                        text = "Save ${formatMinutesClean(selectedMinutes)} Limit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FineTuneMinutesRow(
    selectedMinutes: Int,
    usedMinutesToday: Int,
    onAdjust: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(-5 to "-5m", -1 to "-1m", 1 to "+1m", 5 to "+5m").forEach { (delta, label) ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .expressiveClickable { onAdjust(selectedMinutes + delta) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (usedMinutesToday > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .expressiveClickable { onAdjust(usedMinutesToday.coerceAtLeast(1)) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Lock (${formatMinutesClean(usedMinutesToday)})",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAppTimerBottomSheet(
    isVisible: Boolean,
    discoveredApps: List<DiscoveredDeviceApp>,
    existingPackages: Set<String>,
    onDismiss: () -> Unit,
    onAddAppRule: (
        packageName: String,
        appName: String,
        category: String,
        dailyLimitMinutes: Int
    ) -> Unit
) {
    if (!isVisible) return

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<DiscoveredDeviceApp?>(null) }
    var selectedMinutes by remember { mutableIntStateOf(60) }

    val filteredApps = remember(discoveredApps, searchQuery, existingPackages) {
        val available = discoveredApps.filter { it.packageName !in existingPackages }
        if (searchQuery.isBlank()) {
            available
        } else {
            available.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add App Limit",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    AnimatedContent(
                        targetState = selectedMinutes,
                        transitionSpec = { LockInMotion.numericCounterTransition() },
                        label = "add_limit_badge"
                    ) { mins ->
                        Text(
                            text = formatMinutesClean(mins),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Search Bar for installed apps
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Scrollable List of Installed Apps
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = selectedPackage?.packageName == app.packageName
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .expressiveClickable { selectedPackage = app }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    RealAppIcon(
                                        packageName = app.packageName,
                                        appName = app.appName,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (app.usedMinutesToday > 0) {
                                            "${formatMinutesClean(app.usedMinutesToday)} used today • ${app.category}"
                                        } else {
                                            app.category
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            FineTuneMinutesRow(
                selectedMinutes = selectedMinutes,
                usedMinutesToday = selectedPackage?.usedMinutesToday ?: 0,
                onAdjust = { newMins -> selectedMinutes = newMins.coerceIn(1, 360) }
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DAILY_LIMIT_PRESETS.forEach { mins ->
                    val chosen = selectedMinutes == mins
                    Surface(
                        color = if (chosen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(CircleShape)
                            .expressiveClickable { selectedMinutes = mins }
                            .testTag("preset_minutes_$mins")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatMinutesClean(mins),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (chosen) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val chosenApp = selectedPackage ?: return@Button
                    onAddAppRule(
                        chosenApp.packageName,
                        chosenApp.appName,
                        chosenApp.category,
                        selectedMinutes
                    )
                },
                enabled = selectedPackage != null,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("confirm_add_app_timer_button")
            ) {
                Text(
                    text = if (selectedPackage != null) {
                        "Set ${selectedPackage?.appName} to ${formatMinutesClean(selectedMinutes)}/day"
                    } else {
                        "Select an app above"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectFocusAppBottomSheet(
    isVisible: Boolean,
    pinnedFocusApps: List<PinnedFocusAppEntity>,
    discoveredApps: List<DiscoveredDeviceApp>,
    onDismiss: () -> Unit,
    onSelectFocusApp: (String) -> Unit,
    onAddCustomFocusApp: (packageName: String, appName: String, subtitle: String) -> Unit
) {
    if (!isVisible) return

    var searchQuery by remember { mutableStateOf("") }

    val allReaderCandidates = remember(pinnedFocusApps, discoveredApps, searchQuery) {
        val pinnedPkgs = pinnedFocusApps.map { it.packageName }.toSet()
        val extraReaders = discoveredApps
            .filter { it.packageName !in pinnedPkgs }
            .map {
                PinnedFocusAppEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    subtitle = if (it.isReaderApp) "Installed Reader App" else it.category,
                    isSelected = false,
                    isInstalled = true
                )
            }
        val combined = pinnedFocusApps + extraReaders
        if (searchQuery.isBlank()) {
            combined
        } else {
            combined.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Select Pinned Focus App",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "During Focus Lock, only this app is allowed in the foreground. Switching to any other app triggers the lock barrier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed readers or apps...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allReaderCandidates, key = { it.packageName }) { app ->
                    Surface(
                        color = if (app.isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .expressiveClickable {
                                if (pinnedFocusApps.any { it.packageName == app.packageName }) {
                                    onSelectFocusApp(app.packageName)
                                } else {
                                    onAddCustomFocusApp(app.packageName, app.appName, app.subtitle)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                ) {
                                    RealAppIcon(
                                        packageName = app.packageName,
                                        appName = app.appName,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (app.isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = app.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (app.isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (app.isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPermissionsBottomSheet(
    isVisible: Boolean,
    hasUsageAccess: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasNotifications: Boolean = true,
    isBatteryOptimizedExempt: Boolean,
    isXiaomi: Boolean,
    onDismiss: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenBatteryOptimization: () -> Unit,
    onOpenXiaomiAutostart: (() -> Unit)?
) {
    if (!isVisible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "System Shield Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grant these Android permissions to ensure LockIN stays active in the background, sends timer alerts, and intercepts apps immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Accessibility Blocker
            PermissionRowCard(
                title = "Accessibility Blocker",
                subtitle = "Interception engine (<0.02ms event-driven)",
                isGranted = hasAccessibility,
                onGrantClick = onOpenAccessibility
            )

            // 2. Usage Stats
            PermissionRowCard(
                title = "Usage Access",
                subtitle = "Calculates live daily app screen time minutes",
                isGranted = hasUsageAccess,
                onGrantClick = onOpenUsageAccess
            )

            // 3. Draw Over Other Apps
            PermissionRowCard(
                title = "Display Over Other Apps",
                subtitle = "Renders instant LockIN barrier over blocked apps",
                isGranted = hasOverlay,
                onGrantClick = onOpenOverlay
            )

            // 4. Notifications (Live Focus Timer & 5m Warnings)
            if (onOpenNotifications != null) {
                PermissionRowCard(
                    title = "Timer & Limit Notifications",
                    subtitle = "Live Focus Lock chronometer & 5m remaining alerts",
                    isGranted = hasNotifications,
                    onGrantClick = onOpenNotifications
                )
            }

            // 5. Battery Saver Exemption
            PermissionRowCard(
                title = "Unrestricted Battery",
                subtitle = "Prevents OS from killing background monitoring",
                isGranted = isBatteryOptimizedExempt,
                onGrantClick = onOpenBatteryOptimization
            )

            // 6. Xiaomi Autostart (if device is Xiaomi / Redmi / POCO)
            if (isXiaomi && onOpenXiaomiAutostart != null) {
                PermissionRowCard(
                    title = "HyperOS / MIUI Autostart",
                    subtitle = "Allows LockIN to auto-resume on Xiaomi devices",
                    isGranted = false,
                    onGrantClick = onOpenXiaomiAutostart,
                    buttonLabel = "Configure"
                )
            }

            Button(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
    buttonLabel: String = "Grant"
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .expressiveClickable(onClick = onGrantClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onGrantClick,
                    shape = CircleShape,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
