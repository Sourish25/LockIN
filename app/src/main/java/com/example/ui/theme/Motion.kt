package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset

/**
 * Android 17 / 18 Subtle Spatial Physics & Tactile Motion System.
 * Tuned to feel immediate, physical, and grounded without exaggerated bounce.
 */
object LockInMotion {

    /**
     * Fast, critically damped spring for tactile press states (0.97f scale).
     */
    fun <T> tactileSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 650f
    )

    /**
     * Smooth spatial spring for layout expansion, progress bars, and card state morphs.
     */
    fun <T> spatialSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 380f
    )

    /**
     * Subtle low-bounce spring for expressive badges and active indicators.
     */
    fun <T> subtleBounceSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = 420f
    )

    /**
     * Directional spatial transition when switching top-level tabs.
     */
    fun directionalTabTransition(movingForward: Boolean): ContentTransform {
        val direction = if (movingForward) 1 else -1
        val slideSpring = spring<IntOffset>(
            dampingRatio = 0.88f,
            stiffness = 420f
        )
        val scaleSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 480f
        )
        return (slideInHorizontally(
            animationSpec = slideSpring,
            initialOffsetX = { fullWidth -> (fullWidth * 0.07f * direction).toInt() }
        ) + fadeIn(animationSpec = tween(180)) + scaleIn(
            initialScale = 0.985f,
            animationSpec = scaleSpring
        )).togetherWith(
            slideOutHorizontally(
                animationSpec = slideSpring,
                targetOffsetX = { fullWidth -> -(fullWidth * 0.05f * direction).toInt() }
            ) + fadeOut(animationSpec = tween(130)) + scaleOut(
                targetScale = 0.985f,
                animationSpec = scaleSpring
            )
        )
    }

    /**
     * Subtle vertical counter transition for timer digits and minute pills.
     */
    fun numericCounterTransition(increasing: Boolean = true): ContentTransform {
        val dir = if (increasing) 1 else -1
        val slideSpec = spring<IntOffset>(
            dampingRatio = 0.85f,
            stiffness = 520f
        )
        return (slideInVertically(
            animationSpec = slideSpec,
            initialOffsetY = { height -> (height * 0.45f * dir).toInt() }
        ) + fadeIn(tween(140))).togetherWith(
            slideOutVertically(
                animationSpec = slideSpec,
                targetOffsetY = { height -> -(height * 0.45f * dir).toInt() }
            ) + fadeOut(tween(110))
        )
    }
}

/**
 * Applies a subtle Android 17/18 tactile press scale (default 0.975f) with light haptic feedback
 * when clicked, making cards, chips, and action surfaces feel physically responsive.
 */
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = LockInMotion.tactileSpring(),
        label = "expressive_press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onClick = {
                if (haptic) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
}

/**
 * Adds a subtle tactile scale effect on press to any component that already handles its own click
 * (such as Material 3 Button / FilledTonalButton via shared MutableInteractionSource).
 */
@Composable
fun rememberExpressiveInteractionSource(
    pressedScale: Float = 0.972f
): Pair<MutableInteractionSource, Modifier> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = LockInMotion.tactileSpring(),
        label = "button_press_scale"
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return interactionSource to modifier
}
