package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PinnedFocusAppEntity
import com.example.data.RealAppIcon
import com.example.data.formatMinutesClean
import com.example.ui.components.ExpressiveCloverShape
import com.example.ui.components.scallopedCookieShape
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.expressiveClickable
import com.example.viewmodel.ActiveFocusSessionState

private val FOCUS_DURATION_ROWS = listOf(
    listOf(15, 30, 45),
    listOf(60, 90, 120)
)
private val GiantStarburstShape = scallopedCookieShape(lobes = 12, waveDepthFraction = 0.065f)

@Composable
fun ReadAndLockScreen(
    selectedFocusApp: PinnedFocusAppEntity?,
    activeSession: ActiveFocusSessionState,
    focusHistoryCount: Int = 0,
    isTablet: Boolean,
    onSelectDuration: (Int) -> Unit,
    onStartFocusLock: () -> Unit,
    onLaunchPinnedApp: (String) -> Unit,
    onExtendFocusLock: () -> Unit = {},
    onRequestQuitFocusEarly: () -> Unit,
    onOpenSelectFocusAppSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val rawAppPackage = selectedFocusApp?.packageName?.takeIf { it.isNotBlank() }
        ?: activeSession.pinnedPackageName
    val hasAppSelected = rawAppPackage.isNotBlank()
    val appName = if (hasAppSelected) {
        selectedFocusApp?.appName?.takeIf { it.isNotBlank() }
            ?: activeSession.pinnedAppName.ifBlank { "Selected App" }
    } else {
        "Select Focus App"
    }
    val appSubtitle = if (hasAppSelected) {
        selectedFocusApp?.subtitle?.takeIf { it.isNotBlank() } ?: "Installed Focus App"
    } else {
        "Tap to choose an installed reader or app"
    }
    val appPackage = rawAppPackage

    val infiniteTransition = rememberInfiniteTransition(label = "StarburstRotation")
    val vesselRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VesselAngle"
    )

    if (isTablet) {
        // --- TABLET 2-PANE ADAPTIVE CANONICAL LAYOUT (Optimized for Xiaomi Pad 7 3:2 ratio) ---
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                .testTag("read_and_lock_screen"),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Pane: Pinned Reader App + 3x2 Duration Grid + Focus Telemetry
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Pinned Reader App Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .expressiveClickable(enabled = !activeSession.isActive) {
                            onOpenSelectFocusAppSheet()
                        }
                        .testTag("select_focus_app_card")
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PINNED READER APP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (hasAppSelected) "SINGLE-APP ALLOW" else "UNSELECTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasAppSelected) {
                                    RealAppIcon(
                                        packageName = appPackage,
                                        appName = appName,
                                        modifier = Modifier.size(58.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = appSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!activeSession.isActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .expressiveClickable { onOpenSelectFocusAppSheet() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (hasAppSelected) "Change Reader App" else "Select Installed App",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Symmetric 3x2 Duration Matrix Card (or Active Guard Status Card)
                if (!activeSession.isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                Text(
                                    text = "SELECT DURATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AnimatedContent(
                                    targetState = activeSession.selectedDurationMinutes,
                                    transitionSpec = { LockInMotion.numericCounterTransition() },
                                    label = "tablet_selected_duration"
                                ) { mins ->
                                    Text(
                                        text = formatMinutesClean(mins),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            DurationUniformGrid(
                                selectedDurationMinutes = activeSession.selectedDurationMinutes,
                                pillHeight = 52.dp,
                                unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                onSelectDuration = { mins ->
                                    onSelectDuration(mins)
                                }
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "STRICT INTERCEPTION ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Text(
                                text = "All non-whitelisted apps outside $appName are blocked until the timer finishes or you solve a cognitive puzzle.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Focus Telemetry Card
                FocusTelemetryCard(
                    appName = if (hasAppSelected) appName else "Focus Reader",
                    totalFocusedMinutes = selectedFocusApp?.totalFocusedMinutes ?: 0,
                    focusHistoryCount = focusHistoryCount
                )
            }

            // Right Pane: Framed Expressive Focus Chamber Bento Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(36.dp),
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Chamber Top Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FOCUS CHAMBER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = if (activeSession.isActive) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (activeSession.isActive) "LOCKED IN" else "READY TO LOCK",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeSession.isActive) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Center 12-Lobe Scalloped Starburst Countdown Vessel
                    Box(
                        modifier = Modifier
                            .size(284.dp)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val vesselColor by animateColorAsState(
                            targetValue = if (activeSession.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = LockInMotion.spatialSpring(),
                            label = "tablet_vessel_color"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(if (activeSession.isActive) vesselRotation else 15f)
                                .clip(GiantStarburstShape)
                                .background(vesselColor)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                color = if (activeSession.isActive) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (activeSession.isActive) "FOCUS LOCKED" else "TARGET SPRINT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeSession.isActive) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }

                            val displayTimerText = if (activeSession.isActive) {
                                activeSession.formattedCountdown
                            } else {
                                formatMinutesClean(activeSession.selectedDurationMinutes)
                            }
                            Text(
                                text = displayTimerText,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (activeSession.isActive && activeSession.remainingSeconds >= 3600) 50.sp else 64.sp,
                                lineHeight = 68.sp,
                                letterSpacing = (-2.0).sp,
                                color = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (activeSession.isActive) "Distractions intercepted"
                                else if (hasAppSelected) "Stay locked in $appName"
                                else "Choose an app to begin",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Chamber Bottom Action Bar
                    if (activeSession.isActive) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_lockdown_card"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .clip(CircleShape)
                                        .expressiveClickable { onLaunchPinnedApp(activeSession.pinnedPackageName) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Open $appName",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(60.dp)
                                        .clip(CircleShape)
                                        .expressiveClickable { onExtendFocusLock() }
                                        .testTag("extend_focus_lock_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Extend 15 minutes",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "15m",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(CircleShape)
                                    .expressiveClickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onRequestQuitFocusEarly()
                                    }
                                    .testTag("emergency_unlock_sprint_button")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LockOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Quit Early • Hard Puzzle",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(CircleShape)
                                .expressiveClickable(pressedScale = 0.968f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartFocusLock()
                                }
                                .testTag("start_reading_lockdown_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(ExpressiveCloverShape)
                                        .background(MaterialTheme.colorScheme.onPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Text(
                                    text = if (hasAppSelected) "Lock Into $appName" else "Choose App to Lock Into",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- PHONE VERTICAL LAYOUT ---
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 112.dp)
                .testTag("read_and_lock_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pinned Focus App Pill Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .expressiveClickable(enabled = !activeSession.isActive) {
                        onOpenSelectFocusAppSheet()
                    }
                    .testTag("select_focus_app_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
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
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasAppSelected) {
                                RealAppIcon(
                                    packageName = appPackage,
                                    appName = appName,
                                    modifier = Modifier.size(50.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = appSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!activeSession.isActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "Switch Focus App",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Switch",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Starburst Dial
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 300.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val vesselColor by animateColorAsState(
                    targetValue = if (activeSession.isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = LockInMotion.spatialSpring(),
                    label = "phone_vessel_color"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(if (activeSession.isActive) vesselRotation else 15f)
                        .clip(GiantStarburstShape)
                        .background(vesselColor)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        color = if (activeSession.isActive) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (activeSession.isActive) "LOCKED IN" else "FOCUS DURATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeSession.isActive) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = if (activeSession.isActive) {
                            activeSession.formattedCountdown
                        } else {
                            formatMinutesClean(activeSession.selectedDurationMinutes)
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (activeSession.isActive && activeSession.remainingSeconds >= 3600) 50.sp else 62.sp,
                        lineHeight = 66.sp,
                        letterSpacing = (-2.0).sp,
                        color = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (activeSession.isActive) "Quitting early requires hard puzzle" else "Stay locked in $appName",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Controls
            AnimatedContent(
                targetState = activeSession.isActive,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = LockInMotion.spatialSpring()
                    )) togetherWith (fadeOut(animationSpec = tween(130)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = LockInMotion.spatialSpring()
                    ))
                },
                label = "FocusControlsMorph"
            ) { isLocked ->
                if (isLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_lockdown_card"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clip(CircleShape)
                                    .expressiveClickable { onLaunchPinnedApp(activeSession.pinnedPackageName) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Open $appName",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                                modifier = Modifier
                                    .height(64.dp)
                                    .clip(CircleShape)
                                    .expressiveClickable { onExtendFocusLock() }
                                    .testTag("extend_focus_lock_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Extend 15 minutes",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "15m",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(CircleShape)
                                .expressiveClickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRequestQuitFocusEarly()
                                }
                                .testTag("emergency_unlock_sprint_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Quit Early • Hard Puzzle",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DurationUniformGrid(
                            selectedDurationMinutes = activeSession.selectedDurationMinutes,
                            pillHeight = 50.dp,
                            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            onSelectDuration = { mins ->
                                onSelectDuration(mins)
                            }
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(CircleShape)
                                .expressiveClickable(pressedScale = 0.968f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartFocusLock()
                                }
                                .testTag("start_reading_lockdown_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(ExpressiveCloverShape)
                                        .background(MaterialTheme.colorScheme.onPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Text(
                                    text = if (hasAppSelected) "Lock Into $appName" else "Choose App to Lock Into",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.size(50.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationUniformGrid(
    selectedDurationMinutes: Int,
    pillHeight: androidx.compose.ui.unit.Dp,
    unselectedContainerColor: androidx.compose.ui.graphics.Color,
    onSelectDuration: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FOCUS_DURATION_ROWS.forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { mins ->
                    val selected = selectedDurationMinutes == mins
                    val cornerRadius by animateDpAsState(
                        targetValue = if (selected) 16.dp else 26.dp,
                        animationSpec = LockInMotion.subtleBounceSpring(),
                        label = "DurationMorphCorner"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.tertiaryContainer
                        else unselectedContainerColor,
                        animationSpec = LockInMotion.spatialSpring(),
                        label = "DurationMorphColor"
                    )

                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(cornerRadius),
                        modifier = Modifier
                            .weight(1f)
                            .height(pillHeight)
                            .clip(RoundedCornerShape(cornerRadius))
                            .expressiveClickable { onSelectDuration(mins) }
                            .testTag("duration_pill_$mins")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatMinutesClean(mins),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (selected) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusTelemetryCard(
    appName: String,
    totalFocusedMinutes: Int,
    focusHistoryCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ExpressiveCloverShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total Focus in $appName",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (focusHistoryCount > 0) {
                        "${formatMinutesClean(totalFocusedMinutes)} immersed • $focusHistoryCount sessions"
                    } else {
                        "${formatMinutesClean(totalFocusedMinutes)} immersed reading"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
