package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.BuildConfig
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppTimerRuleEntity
import com.example.data.DiscoveredDeviceApp
import com.example.data.FocusExceptionAppEntity
import com.example.data.PinnedFocusAppEntity
import com.example.data.RealAppIcon
import com.example.data.formatMinutesClean
import com.example.ui.theme.ExpressiveFeedbackEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.expressiveClickable

private val DAILY_LIMIT_PRESET_ROWS = listOf(
    listOf(1, 5, 10, 15, 30),
    listOf(45, 60, 90, 120, 180)
)

/**
 * Isolates child LazyColumn / vertical scroll gestures from ModalBottomSheet's internal
 * drag-to-dismiss nested scroll handler so scrolling downward inside an app list
 * NEVER drags or closes the bottom sheet.
 */
@Composable
private fun rememberSheetScrollIsolationConnection(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Consume all remaining vertical scroll delta so ModalBottomSheet never drags down
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // Consume all remaining vertical fling velocity so downward flings never dismiss sheet
                return Velocity(0f, available.y)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAppLimitBottomSheet(
    rule: AppTimerRuleEntity?,
    onDismiss: () -> Unit,
    onSaveLimit: (Int) -> Unit,
    onRemoveRule: () -> Unit
) {
    if (rule == null) return

    val context = LocalContext.current
    val scrollIsolation = rememberSheetScrollIsolationConnection()
    var selectedMinutes by remember(rule.dailyLimitMinutes) {
        mutableIntStateOf(rule.dailyLimitMinutes.coerceAtLeast(1))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = 600.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollIsolation)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ -> change.consume() }
                }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.appName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatMinutesClean(rule.usedMinutesToday)} used today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    AnimatedContent(
                        targetState = selectedMinutes,
                        transitionSpec = {
                            val rollDown = targetState < initialState
                            if (rollDown) {
                                (slideInVertically { -it } + fadeIn()) togetherWith
                                        (slideOutVertically { it } + fadeOut())
                            } else {
                                (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                            }
                        },
                        label = "edit_limit_badge"
                    ) { mins ->
                        Text(
                            text = formatMinutesClean(mins),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            FineTuneMinutesRow(
                selectedMinutes = selectedMinutes,
                usedMinutesToday = rule.usedMinutesToday,
                onAdjust = { newMins ->
                    val clamped = newMins.coerceIn(1, 360)
                    if (clamped != selectedMinutes) {
                        ExpressiveFeedbackEngine.playSlotTick(
                            context,
                            if (clamped > selectedMinutes) 1.15f else 0.9f
                        )
                        selectedMinutes = clamped
                    }
                }
            )

            Text(
                text = "Daily time limit presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PresetMinutesGrid(
                selectedMinutes = selectedMinutes,
                onSelectMinutes = { mins ->
                    if (mins != selectedMinutes) {
                        ExpressiveFeedbackEngine.playPillTap(context)
                        selectedMinutes = mins
                    }
                }
            )

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
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove limit"
                    )
                }

                Button(
                    onClick = {
                        ExpressiveFeedbackEngine.playLockActivated(context)
                        onSaveLimit(selectedMinutes)
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("save_app_limit_button")
                ) {
                    Text(
                        text = "Save ${formatMinutesClean(selectedMinutes)} Limit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PresetMinutesGrid(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DAILY_LIMIT_PRESET_ROWS.forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPresets.forEach { mins ->
                    val chosen = selectedMinutes == mins
                    val cornerRadius by animateDpAsState(
                        targetValue = if (chosen) 14.dp else 22.dp,
                        animationSpec = LockInMotion.subtleBounceSpring(),
                        label = "PresetCorner_$mins"
                    )
                    val bgColor by animateColorAsState(
                        targetValue = if (chosen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = LockInMotion.spatialSpring(),
                        label = "PresetBg_$mins"
                    )

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(cornerRadius),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(cornerRadius))
                            .expressiveClickable { onSelectMinutes(mins) }
                            .testTag("preset_minutes_$mins")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = formatMinutesClean(mins),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (chosen) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
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
                    .height(42.dp)
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
                    .height(42.dp)
                    .clip(CircleShape)
                    .expressiveClickable { onAdjust(usedMinutesToday.coerceAtLeast(1)) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Lock (${formatMinutesClean(usedMinutesToday)})",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val context = LocalContext.current
    val scrollIsolation = rememberSheetScrollIsolationConnection()
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
        sheetMaxWidth = 600.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollIsolation)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ -> change.consume() }
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        transitionSpec = {
                            val rollDown = targetState < initialState
                            if (rollDown) {
                                (slideInVertically { -it } + fadeIn()) togetherWith
                                        (slideOutVertically { it } + fadeOut())
                            } else {
                                (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                            }
                        },
                        label = "add_limit_badge"
                    ) { mins ->
                        Text(
                            text = formatMinutesClean(mins),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
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

            // Scroll-Isolated List of Installed Apps (uses contentPadding so rows never clip)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(228.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .nestedScroll(scrollIsolation)
                    .testTag("add_app_limit_list"),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            .expressiveClickable {
                                ExpressiveFeedbackEngine.playPillTap(context)
                                selectedPackage = app
                            }
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

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (app.usedMinutesToday > 0) {
                                            "${formatMinutesClean(app.usedMinutesToday)} used today • ${app.category}"
                                        } else {
                                            app.category
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                onAdjust = { newMins ->
                    val clamped = newMins.coerceIn(1, 360)
                    if (clamped != selectedMinutes) {
                        ExpressiveFeedbackEngine.playSlotTick(
                            context,
                            if (clamped > selectedMinutes) 1.15f else 0.9f
                        )
                        selectedMinutes = clamped
                    }
                }
            )

            // Symmetric 5x2 Preset Pill Matrix (no orphan 2-pill wrap row)
            PresetMinutesGrid(
                selectedMinutes = selectedMinutes,
                onSelectMinutes = { mins ->
                    if (mins != selectedMinutes) {
                        ExpressiveFeedbackEngine.playPillTap(context)
                        selectedMinutes = mins
                    }
                }
            )

            Button(
                onClick = {
                    val chosenApp = selectedPackage ?: return@Button
                    ExpressiveFeedbackEngine.playLockActivated(context)
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
                    .height(56.dp)
                    .testTag("confirm_add_app_timer_button")
            ) {
                Text(
                    text = if (selectedPackage != null) {
                        "Set ${selectedPackage?.appName} to ${formatMinutesClean(selectedMinutes)}/day"
                    } else {
                        "Select an app above"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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

    val context = LocalContext.current
    val scrollIsolation = rememberSheetScrollIsolationConnection()
    var searchQuery by remember { mutableStateOf("") }

    val allFocusCandidates = remember(pinnedFocusApps, discoveredApps, searchQuery) {
        val pinnedPkgs = pinnedFocusApps.map { it.packageName }.toSet()
        val extraApps = discoveredApps
            .filter { it.packageName !in pinnedPkgs }
            .map {
                PinnedFocusAppEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    subtitle = it.category,
                    isSelected = false,
                    isInstalled = true
                )
            }
        val combined = pinnedFocusApps + extraApps
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
        sheetMaxWidth = 600.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollIsolation)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ -> change.consume() }
                }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Select Pinned Focus App",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "During Focus Lock, only this app (plus your Allowed Exception Apps) can stay open in the foreground.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .nestedScroll(scrollIsolation),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allFocusCandidates, key = { it.packageName }) { app ->
                    Surface(
                        color = if (app.isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .expressiveClickable {
                                ExpressiveFeedbackEngine.playPillTap(context)
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
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                ) {
                                    RealAppIcon(
                                        packageName = app.packageName,
                                        appName = app.appName,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (app.isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (app.isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectFocusExceptionsBottomSheet(
    isVisible: Boolean,
    exceptionApps: List<FocusExceptionAppEntity>,
    discoveredApps: List<DiscoveredDeviceApp>,
    pinnedFocusPackageName: String = "",
    onDismiss: () -> Unit,
    onToggleExceptionApp: (String, String, String) -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val scrollIsolation = rememberSheetScrollIsolationConnection()
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember(exceptionApps) {
        exceptionApps.map { it.packageName }.toSet()
    }

    val candidateApps = remember(discoveredApps, exceptionApps, searchQuery, pinnedFocusPackageName) {
        val merged = mutableMapOf<String, DiscoveredDeviceApp>()
        discoveredApps.forEach { app ->
            if (app.packageName != pinnedFocusPackageName) {
                merged[app.packageName] = app
            }
        }
        exceptionApps.forEach { exc ->
            if (!merged.containsKey(exc.packageName) && exc.packageName != pinnedFocusPackageName) {
                merged[exc.packageName] = DiscoveredDeviceApp(
                    packageName = exc.packageName,
                    appName = exc.appName,
                    category = exc.category,
                    isInstalledOnDevice = true
                )
            }
        }

        merged.values
            .filter {
                searchQuery.isBlank() ||
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<DiscoveredDeviceApp> { it.packageName in selectedPackages }
                    .thenByDescending {
                        it.category == "Payments & Banking" || it.category == "Emergency & Calls"
                    }
                    .thenBy { it.appName.lowercase() }
            )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = 600.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollIsolation)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ -> change.consume() }
                }
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .testTag("select_focus_exceptions_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Allowed Exception Apps",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose essential apps (such as Google Pay, Wallet, Phone, Authenticator, or Maps) that you can open freely during Focus Lock without triggering the puzzle barrier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Built-in safety: System Phone Dialer & Emergency Calls are always allowed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search GPay, Phone, Wallet, Maps, WhatsApp...") },
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
                    .height(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .nestedScroll(scrollIsolation),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(candidateApps, key = { it.packageName }) { app ->
                    val isAllowed = app.packageName in selectedPackages
                    Surface(
                        color = if (isAllowed) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .expressiveClickable {
                                ExpressiveFeedbackEngine.playPillTap(context)
                                onToggleExceptionApp(app.packageName, app.appName, app.category)
                            }
                            .testTag("toggle_exception_app_${app.packageName}")
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

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isAllowed) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isAllowed) "Allowed during Focus Lock • ${app.category}"
                                        else app.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isAllowed) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                color = if (isAllowed) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isAllowed) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Allowed",
                                            tint = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isAllowed) "Allowed" else "Allow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAllowed) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    ExpressiveFeedbackEngine.playPillTap(context)
                    onDismiss()
                },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Done (${exceptionApps.size} Allowed)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
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
    onOpenAppDetailsSettings: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenBatteryOptimization: () -> Unit,
    onOpenXiaomiAutostart: (() -> Unit)?,
    onCheckForUpdates: (() -> Unit)? = null
) {
    if (!isVisible) return

    val scrollIsolation = rememberSheetScrollIsolationConnection()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = 600.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(scrollIsolation)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Protection & Permissions",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grant these Android permissions to ensure LockIN stays active in the background, sends timer alerts, and intercepts apps immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Android 13-16 Restricted Settings / Play Protect Sideload Unlock Helper
            if ((!hasAccessibility || !hasOverlay) && onOpenAppDetailsSettings != null) {
                PermissionRowCard(
                    title = "Unlock 'Restricted Settings' (Android 13+)",
                    subtitle = "If Accessibility is greyed out: tap App Info -> top-right ⋮ menu -> Allow restricted settings",
                    isGranted = false,
                    onGrantClick = onOpenAppDetailsSettings,
                    buttonLabel = "App Info"
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
                    .height(54.dp)
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
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
