package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkExpressiveScheme = darkColorScheme(
    primary = ExpressivePrimaryDark,
    onPrimary = ExpressiveOnPrimaryDark,
    primaryContainer = ExpressivePrimaryContainerDark,
    onPrimaryContainer = ExpressiveOnPrimaryContainerDark,
    secondary = ExpressiveSecondaryDark,
    onSecondary = ExpressiveOnSecondaryDark,
    secondaryContainer = ExpressiveSecondaryContainerDark,
    onSecondaryContainer = ExpressiveOnSecondaryContainerDark,
    tertiary = ExpressiveTertiaryDark,
    onTertiary = ExpressiveOnTertiaryDark,
    tertiaryContainer = ExpressiveTertiaryContainerDark,
    onTertiaryContainer = ExpressiveOnTertiaryContainerDark,
    error = ExpressiveErrorDark,
    onError = ExpressiveOnErrorDark,
    errorContainer = ExpressiveErrorContainerDark,
    onErrorContainer = ExpressiveOnErrorContainerDark,
    background = ExpressiveBackgroundDark,
    onBackground = ExpressiveOnBackgroundDark,
    surface = ExpressiveSurfaceDark,
    onSurface = ExpressiveOnSurfaceDark,
    surfaceVariant = ExpressiveSurfaceVariantDark,
    onSurfaceVariant = ExpressiveOnSurfaceVariantDark,
    surfaceContainerLowest = ExpressiveSurfaceContainerLowestDark,
    surfaceContainerLow = ExpressiveSurfaceContainerLowDark,
    surfaceContainer = ExpressiveSurfaceContainerDark,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighDark,
    surfaceContainerHighest = ExpressiveSurfaceContainerHighestDark,
    outline = ExpressiveOutlineDark,
    outlineVariant = ExpressiveOutlineVariantDark
)

private val LightExpressiveScheme = lightColorScheme(
    primary = ExpressivePrimaryLight,
    onPrimary = ExpressiveOnPrimaryLight,
    primaryContainer = ExpressivePrimaryContainerLight,
    onPrimaryContainer = ExpressiveOnPrimaryContainerLight,
    secondary = ExpressiveSecondaryLight,
    onSecondary = ExpressiveOnSecondaryLight,
    secondaryContainer = ExpressiveSecondaryContainerLight,
    onSecondaryContainer = ExpressiveOnSecondaryContainerLight,
    tertiary = ExpressiveTertiaryLight,
    onTertiary = ExpressiveOnTertiaryLight,
    tertiaryContainer = ExpressiveTertiaryContainerLight,
    onTertiaryContainer = ExpressiveOnTertiaryContainerLight,
    error = ExpressiveErrorLight,
    onError = ExpressiveOnErrorLight,
    errorContainer = ExpressiveErrorContainerLight,
    onErrorContainer = ExpressiveOnErrorContainerLight,
    background = ExpressiveBackgroundLight,
    onBackground = ExpressiveOnBackgroundLight,
    surface = ExpressiveSurfaceLight,
    onSurface = ExpressiveOnSurfaceLight,
    surfaceVariant = ExpressiveSurfaceVariantLight,
    onSurfaceVariant = ExpressiveOnSurfaceVariantLight,
    surfaceContainerLowest = ExpressiveSurfaceContainerLowestLight,
    surfaceContainerLow = ExpressiveSurfaceContainerLowLight,
    surfaceContainer = ExpressiveSurfaceContainerLight,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighLight,
    surfaceContainerHighest = ExpressiveSurfaceContainerHighestLight,
    outline = ExpressiveOutlineLight,
    outlineVariant = ExpressiveOutlineVariantLight
)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(44.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    // When dynamicColor is false, uses the bold Android 16 Material 3 Expressive showcase palette
    // so even emulators with muted default wallpapers look ultra-expressive out of the box!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkExpressiveScheme
        else -> LightExpressiveScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}
