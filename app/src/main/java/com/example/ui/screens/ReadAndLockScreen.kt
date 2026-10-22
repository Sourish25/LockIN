package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusExceptionAppEntity
import com.example.data.PinnedFocusAppEntity
import com.example.data.RealAppIcon
import com.example.data.formatMinutesClean
import com.example.ui.components.ExpressiveCloverShape
import com.example.ui.components.scallopedCookieShape
import com.example.ui.theme.ExpressiveFeedbackEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LockInMotion
import com.example.ui.theme.expressiveClickable
import com.example.viewmodel.ActiveFocusSessionState
import kotlin.math.abs
import kotlinx.coroutines.delay

private val FOCUS_DURATION_ROWS = listOf(
    listOf(15, 30, 45),
    listOf(60, 90, 120)
)
private val GiantStarburstShape = scallopedCookieShape(lobes = 12, waveDepthFraction = 0.065f)

@Composable
fun FocusLockScreen(
    selectedFocusApp: PinnedFocusAppEntity?,
    activeSession: ActiveFocusSessionState,
    focusExceptionApps: List<FocusExceptionAppEntity> = emptyList(),
    focusHistoryCount: Int = 0,
    isTablet: Boolean,
    onSelectDuration: (Int) -> Unit,
    onStartFocusLock: () -> Unit,
    onLaunchPinnedApp: (String) -> Unit,
    onLaunchExceptionApp: (String) -> Unit = {},
    onExtendFocusLock: () -> Unit = {},
    onRequestQuitFocusEarly: () -> Unit,
    onOpenSelectFocusAppSheet: () -> Unit,
    onOpenSelectFocusExceptionsSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        "Tap to choose an installed app to lock into"
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("read_and_lock_screen")
    ) {
        val useTwoPane = isTablet && maxWidth >= 740.dp && maxWidth > maxHeight

        if (useTwoPane) {
            // --- LANDSCAPE TABLET 2-PANE ADAPTIVE CANONICAL LAYOUT ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Pane: Pinned Focus App + 3x2 Duration Grid + Allowed Exceptions + Focus Telemetry
                Column(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Pinned Focus App Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .expressiveClickable(enabled = !activeSession.isActive) {
                                ExpressiveFeedbackEngine.playPillTap(context)
                                onOpenSelectFocusAppSheet()
                            }
                            .testTag("select_focus_app_card")
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
                                Text(
                                    text = "IMMERSION FOCUS APP",
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
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasAppSelected) {
                                        RealAppIcon(
                                            packageName = appPackage,
                                            appName = appName,
                                            modifier = Modifier.size(52.dp)
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
                                        .expressiveClickable {
                                            ExpressiveFeedbackEngine.playPillTap(context)
                                            onOpenSelectFocusAppSheet()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
                                            text = if (hasAppSelected) "Change Focus App" else "Select Target App",
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
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth()
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
                                        text = "SELECT DURATION",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AnimatedContent(
                                        targetState = activeSession.selectedDurationMinutes,
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
                                    pillHeight = 46.dp,
                                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    onSelectDuration = { mins ->
                                        ExpressiveFeedbackEngine.playPillTap(context)
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

                    // 3. Allowed Exception Apps (Emergency Calls, GPay, Utilities)
                    FocusExceptionAppsCard(
                        exceptionApps = focusExceptionApps,
                        isSessionActive = activeSession.isActive,
                        onOpenManageSheet = {
                            ExpressiveFeedbackEngine.playPillTap(context)
                            onOpenSelectFocusExceptionsSheet()
                        },
                        onLaunchExceptionApp = onLaunchExceptionApp
                    )

                    // 4. Focus Telemetry Card
                    FocusTelemetryCard(
                        appName = if (hasAppSelected) appName else "Focus Target",
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

                        // Center 12-Lobe Scalloped Starburst Countdown Vessel with Slot-Machine Digital Clock
                        Box(
                            modifier = Modifier
                                .size(288.dp)
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
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Surface(
                                    color = if (activeSession.isActive) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = if (activeSession.isActive) "LIVE COUNTDOWN" else "SLOT TIMER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeSession.isActive) MaterialTheme.colorScheme.onTertiaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }

                                ExpressiveSlotClockDisplay(
                                    isActive = activeSession.isActive,
                                    selectedDurationMinutes = activeSession.selectedDurationMinutes,
                                    remainingSeconds = activeSession.remainingSeconds,
                                    textColor = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
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
                                            .expressiveClickable {
                                                ExpressiveFeedbackEngine.playPillTap(context)
                                                onLaunchPinnedApp(activeSession.pinnedPackageName)
                                            }
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
                                            .expressiveClickable {
                                                ExpressiveFeedbackEngine.playLockActivated(context)
                                                onExtendFocusLock()
                                            }
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
                                        ExpressiveFeedbackEngine.playLockActivated(context)
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
            // --- PHONE & PORTRAIT TABLET VERTICAL LAYOUT ---
            val isWidePortrait = maxWidth >= 600.dp
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = if (isWidePortrait) 28.dp else 18.dp,
                            end = if (isWidePortrait) 28.dp else 18.dp,
                            top = 8.dp,
                            bottom = 112.dp
                        ),
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
                                ExpressiveFeedbackEngine.playPillTap(context)
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

                    // Starburst Dial with Slot-Machine Digital Clock (HH:MM:SS / MM:SS)
                    Box(
                        modifier = Modifier
                            .sizeIn(maxWidth = 328.dp, maxHeight = 328.dp)
                            .fillMaxWidth(0.88f)
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
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)
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

                            ExpressiveSlotClockDisplay(
                                isActive = activeSession.isActive,
                                selectedDurationMinutes = activeSession.selectedDurationMinutes,
                                remainingSeconds = activeSession.remainingSeconds,
                                textColor = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = if (activeSession.isActive) "Hard puzzle required to exit" else "Stay locked in $appName",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (activeSession.isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                            .expressiveClickable {
                                                ExpressiveFeedbackEngine.playPillTap(context)
                                                onLaunchPinnedApp(activeSession.pinnedPackageName)
                                            }
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
                                            .expressiveClickable {
                                                ExpressiveFeedbackEngine.playLockActivated(context)
                                                onExtendFocusLock()
                                            }
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
                                        ExpressiveFeedbackEngine.playPillTap(context)
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
                                            ExpressiveFeedbackEngine.playLockActivated(context)
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

                    // Allowed Exception Apps Card (Emergency Calls, GPay, Utilities)
                    FocusExceptionAppsCard(
                        exceptionApps = focusExceptionApps,
                        isSessionActive = activeSession.isActive,
                        onOpenManageSheet = {
                            ExpressiveFeedbackEngine.playPillTap(context)
                            onOpenSelectFocusExceptionsSheet()
                        },
                        onLaunchExceptionApp = onLaunchExceptionApp
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Slot-Machine / Odometer Digital Clock.
 * - Always displays seconds (`MM:SS` or `H:MM:SS`) in both preset selection and live countdown.
 * - When switching presets (e.g. `1h` -> `1h 30m` or `1h 30m` -> `45m`), rapidly spins through
 *   intermediate time values like a slot machine reel with micro-tick haptics and audio.
 * - When time decreases (or ticks down in real time every second), digits roll downward from
 *   top to bottom. When time increases, digits roll upward from bottom to top.
 */
@Composable
private fun ExpressiveSlotClockDisplay(
    isActive: Boolean,
    selectedDurationMinutes: Int,
    remainingSeconds: Int,
    textColor: Color
) {
    val context = LocalContext.current
    val targetTotalSeconds = if (isActive) {
        remainingSeconds.coerceAtLeast(0)
    } else {
        (selectedDurationMinutes.coerceAtLeast(1)) * 60
    }

    var displayedSeconds by remember { mutableIntStateOf(targetTotalSeconds) }
    var isRollingDownward by remember { mutableStateOf(true) }

    LaunchedEffect(targetTotalSeconds, isActive) {
        val startSecs = displayedSeconds
        val diff = targetTotalSeconds - startSecs
        if (diff == 0) return@LaunchedEffect

        isRollingDownward = diff < 0

        if (isActive || abs(diff) <= 60) {
            // Real-time 1-second tick down (or small adjustment): roll directly on every tick
            displayedSeconds = targetTotalSeconds
        } else {
            // Rapid slot-machine spin across intermediate minutes & seconds when switching presets!
            val steps = 7
            for (step in 1..steps) {
                val fraction = step.toFloat() / steps.toFloat()
                val interpolated = startSecs + (diff * fraction).toInt()
                // Add intermediate second reel movement on steps 1..steps-1 so the seconds reel spins too
                val spinSecondsOffset = if (step < steps) {
                    val scramble = ((steps - step) * 13) % 60
                    if (diff > 0) scramble else -scramble
                } else {
                    0
                }
                displayedSeconds = (interpolated + spinSecondsOffset).coerceAtLeast(0)
                val pitch = if (diff > 0) {
                    0.92f + (fraction * 0.32f)
                } else {
                    1.22f - (fraction * 0.30f)
                }
                ExpressiveFeedbackEngine.playSlotTick(context, pitch)
                delay(36L)
            }
            displayedSeconds = targetTotalSeconds
        }
    }

    val formattedClock = remember(displayedSeconds) {
        formatSecondsWithHoursMinutesSeconds(displayedSeconds)
    }
    val isLongFormat = formattedClock.length >= 7 // e.g. "1:30:00"
    val fontSize = if (isLongFormat) 42.sp else 52.sp
    val lineHeight = if (isLongFormat) 48.sp else 58.sp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.testTag("slot_machine_clock_display")
    ) {
        formattedClock.forEachIndexed { index, ch ->
            // Key from the right so minutes/seconds reels stay aligned when hours appear/disappear
            val positionFromRight = formattedClock.length - index
            androidx.compose.runtime.key(positionFromRight) {
                if (ch == ':') {
                    Text(
                        text = ":",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = textColor.copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 1.dp)
                    )
                } else {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            val springSpec = spring<androidx.compose.ui.unit.IntOffset>(
                                dampingRatio = 0.80f,
                                stiffness = 520f
                            )
                            if (isRollingDownward) {
                                // Numbers roll DOWNWARD (from top to bottom) when lessening time or ticking down
                                (slideInVertically(animationSpec = springSpec) { fullHeight -> -fullHeight } +
                                        fadeIn(animationSpec = tween(90))) togetherWith
                                        (slideOutVertically(animationSpec = springSpec) { fullHeight -> fullHeight } +
                                                fadeOut(animationSpec = tween(75)))
                            } else {
                                // Numbers roll UPWARD (from bottom to top) when increasing time
                                (slideInVertically(animationSpec = springSpec) { fullHeight -> fullHeight } +
                                        fadeIn(animationSpec = tween(90))) togetherWith
                                        (slideOutVertically(animationSpec = springSpec) { fullHeight -> -fullHeight } +
                                                fadeOut(animationSpec = tween(75)))
                            } using SizeTransform(clip = true)
                        },
                        label = "slot_digit_$positionFromRight"
                    ) { digitChar ->
                        Text(
                            text = digitChar.toString(),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            letterSpacing = (-1.2).sp,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun formatSecondsWithHoursMinutesSeconds(totalSeconds: Int): String {
    val safeSecs = totalSeconds.coerceAtLeast(0)
    val hours = safeSecs / 3600
    val mins = (safeSecs % 3600) / 60
    val secs = safeSecs % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusExceptionAppsCard(
    exceptionApps: List<FocusExceptionAppEntity>,
    isSessionActive: Boolean,
    onOpenManageSheet: () -> Unit,
    onLaunchExceptionApp: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("focus_exception_apps_card")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ALLOWED EXCEPTION APPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Emergency calls, GPay & essential utilities allowed during lock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .expressiveClickable { onOpenManageSheet() }
                        .testTag("open_focus_exceptions_sheet_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Manage Exception Apps",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (exceptionApps.isEmpty()) "Add Apps" else "Edit (${exceptionApps.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (exceptionApps.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .expressiveClickable { onOpenManageSheet() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Phone Dialer & Emergency Calls are always allowed. Tap to add Google Pay, WhatsApp, or banking apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exceptionApps.forEach { app ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                            modifier = Modifier
                                .clip(CircleShape)
                                .expressiveClickable {
                                    if (isSessionActive) {
                                        onLaunchExceptionApp(app.packageName)
                                    } else {
                                        onOpenManageSheet()
                                    }
                                }
                                .testTag("exception_app_chip_${app.packageName}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                ) {
                                    RealAppIcon(
                                        packageName = app.packageName,
                                        appName = app.appName,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSessionActive) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open ${app.appName}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
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
    unselectedContainerColor: Color,
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
                        "${formatMinutesClean(totalFocusedMinutes)} immersed focus"
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

/**
 * Backward compatibility alias for [FocusLockScreen].
 */
@Composable
fun ReadAndLockScreen(
    selectedFocusApp: PinnedFocusAppEntity?,
    activeSession: ActiveFocusSessionState,
    focusExceptionApps: List<FocusExceptionAppEntity> = emptyList(),
    focusHistoryCount: Int = 0,
    isTablet: Boolean,
    onSelectDuration: (Int) -> Unit,
    onStartFocusLock: () -> Unit,
    onLaunchPinnedApp: (String) -> Unit,
    onLaunchExceptionApp: (String) -> Unit = {},
    onExtendFocusLock: () -> Unit = {},
    onRequestQuitFocusEarly: () -> Unit,
    onOpenSelectFocusAppSheet: () -> Unit,
    onOpenSelectFocusExceptionsSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) = FocusLockScreen(
    selectedFocusApp = selectedFocusApp,
    activeSession = activeSession,
    focusExceptionApps = focusExceptionApps,
    focusHistoryCount = focusHistoryCount,
    isTablet = isTablet,
    onSelectDuration = onSelectDuration,
    onStartFocusLock = onStartFocusLock,
    onLaunchPinnedApp = onLaunchPinnedApp,
    onLaunchExceptionApp = onLaunchExceptionApp,
    onExtendFocusLock = onExtendFocusLock,
    onRequestQuitFocusEarly = onRequestQuitFocusEarly,
    onOpenSelectFocusAppSheet = onOpenSelectFocusAppSheet,
    onOpenSelectFocusExceptionsSheet = onOpenSelectFocusExceptionsSheet,
    modifier = modifier
)
