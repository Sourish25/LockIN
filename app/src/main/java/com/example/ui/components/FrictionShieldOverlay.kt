package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.ChallengeMode
import com.example.viewmodel.FrictionChallengeState
import com.example.viewmodel.FrictionReason

@Composable
fun FrictionShieldOverlay(
    challenge: FrictionChallengeState,
    onSwitchMode: (ChallengeMode) -> Unit,
    onTapMemoryTile: (Int) -> Unit,
    onReplayMemoryPattern: () -> Unit,
    onPhraseChanged: (String) -> Unit,
    onConfirmPhraseUnlock: () -> Unit,
    onMathInputChanged: (String) -> Unit,
    onSubmitMathStep: () -> Unit,
    onKeepLocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    BackHandler {
        onKeepLocked()
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("friction_shield_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Clean Header Pill
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (challenge.reason) {
                            FrictionReason.QUIT_FOCUS_LOCK_EARLY -> "Focus Lock Active"
                            FrictionReason.UNLOCK_BLOCKED_APP -> "Daily Limit Reached"
                            FrictionReason.EDIT_LOCKED_APP_SETTINGS -> "Settings Locked"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Title & Description
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val title = when (challenge.reason) {
                    FrictionReason.QUIT_FOCUS_LOCK_EARLY ->
                        "Stay in ${challenge.targetAppName ?: "your reader"}?"
                    FrictionReason.UNLOCK_BLOCKED_APP ->
                        "${challenge.targetAppName ?: "App"} is blocked for today"
                    FrictionReason.EDIT_LOCKED_APP_SETTINGS ->
                        "Unlock ${challenge.targetAppName ?: "App"} settings"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = Alignment.CenterHorizontally.let { TextAlign.Center }
                )

                val subtitle = when (challenge.reason) {
                    FrictionReason.QUIT_FOCUS_LOCK_EARLY ->
                        "To prevent subconscious quitting mid-chapter, solve the puzzle below only if you truly need to exit."
                    FrictionReason.UNLOCK_BLOCKED_APP ->
                        "You reached your daily limit. Solve the puzzle to grant a 10-minute override."
                    FrictionReason.EDIT_LOCKED_APP_SETTINGS ->
                        "This app has reached its daily limit. Complete the puzzle below to modify its timer."
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // 3-Mode Segmented Selector (Pattern / Math / Phrase)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PuzzleModeTab(
                        label = "Pattern",
                        icon = Icons.Filled.Apps,
                        selected = challenge.mode == ChallengeMode.MEMORY_GRID,
                        onClick = { onSwitchMode(ChallengeMode.MEMORY_GRID) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_pattern_tab")
                    )
                    PuzzleModeTab(
                        label = "Math",
                        icon = Icons.Filled.Calculate,
                        selected = challenge.mode == ChallengeMode.MATH_CHAIN,
                        onClick = { onSwitchMode(ChallengeMode.MATH_CHAIN) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_math_tab")
                    )
                    PuzzleModeTab(
                        label = "Phrase",
                        icon = Icons.Filled.Keyboard,
                        selected = challenge.mode == ChallengeMode.PHRASE,
                        onClick = { onSwitchMode(ChallengeMode.PHRASE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_phrase_tab")
                    )
                }
            }

            // Active Puzzle Container
            when (challenge.mode) {
                ChallengeMode.MEMORY_GRID -> {
                    MemoryGridPuzzleCard(
                        challenge = challenge,
                        onTapTile = { index ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTapMemoryTile(index)
                        },
                        onReplayPattern = onReplayMemoryPattern
                    )
                }
                ChallengeMode.MATH_CHAIN -> {
                    HardMathPuzzleCard(
                        challenge = challenge,
                        onMathInputChanged = onMathInputChanged,
                        onSubmitMathStep = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSubmitMathStep()
                        }
                    )
                }
                ChallengeMode.PHRASE -> {
                    PhraseTypingPuzzleCard(
                        challenge = challenge,
                        onPhraseChanged = onPhraseChanged,
                        onConfirmUnlock = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirmPhraseUnlock()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Primary Escape Button: Stay Focused
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onKeepLocked()
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("keep_locked_return_button")
            ) {
                Text(
                    text = if (challenge.reason == FrictionReason.QUIT_FOCUS_LOCK_EARLY) {
                        "Keep Reading (${challenge.targetAppName ?: "Locked"})"
                    } else {
                        "Keep App Locked"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PuzzleModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemoryGridPuzzleCard(
    challenge: FrictionChallengeState,
    onTapTile: (Int) -> Unit,
    onReplayPattern: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(36.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challenge.gridStatusMessage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalButton(
                    onClick = onReplayPattern,
                    shape = CircleShape,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "New pattern",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replay", style = MaterialTheme.typography.labelMedium)
                }
            }

            // 4x4 Expressive Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0 until 4) {
                            val tileIndex = row * 4 + col
                            val isFlashing = challenge.currentlyFlashingTile == tileIndex
                            val clickedOrder = challenge.userClickedSequence.indexOf(tileIndex)
                            val isClicked = clickedOrder >= 0

                            val tileColor by animateColorAsState(
                                targetValue = when {
                                    isFlashing -> MaterialTheme.colorScheme.primary
                                    isClicked -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                animationSpec = tween(durationMillis = 150),
                                label = "TileColor"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(tileColor)
                                    .clickable(enabled = !challenge.isRevealingPattern && !isClicked) {
                                        onTapTile(tileIndex)
                                    }
                                    .testTag("memory_tile_$tileIndex"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isClicked) {
                                    Text(
                                        text = "${clickedOrder + 1}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
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
private fun HardMathPuzzleCard(
    challenge: FrictionChallengeState,
    onMathInputChanged: (String) -> Unit,
    onSubmitMathStep: () -> Unit
) {
    val problem = challenge.currentMathProblem

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(36.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mental Math Chain",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Step ${problem.stepIndex} of ${problem.totalSteps}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${problem.expressionText} = ?",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 28.dp, horizontal = 16.dp)
                )
            }

            if (challenge.mathErrorMessage != null) {
                Text(
                    text = challenge.mathErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = challenge.mathInput,
                onValueChange = onMathInputChanged,
                placeholder = { Text("Enter answer") },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmitMathStep() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friction_math_input")
            )

            FilledTonalButton(
                onClick = onSubmitMathStep,
                enabled = challenge.mathInput.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_math_step_button")
            ) {
                Text(
                    text = if (problem.stepIndex < problem.totalSteps) "Next Equation" else "Verify & Unlock",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun PhraseTypingPuzzleCard(
    challenge: FrictionChallengeState,
    onPhraseChanged: (String) -> Unit,
    onConfirmUnlock: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(36.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Type the exact phrase below (copy-paste disabled):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = challenge.requiredPhrase,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(20.dp)
                )
            }

            OutlinedTextField(
                value = challenge.typedPhrase,
                onValueChange = onPhraseChanged,
                placeholder = { Text("Start typing here...") },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (challenge.isPhraseMatched) onConfirmUnlock() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friction_phrase_input")
            )

            FilledTonalButton(
                onClick = onConfirmUnlock,
                enabled = challenge.isPhraseMatched,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("confirm_phrase_unlock_button")
            ) {
                Icon(
                    imageVector = if (challenge.isPhraseMatched) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm Unlock",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
