package com.example.ui.components

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Authentic Material 3 Expressive 12-lobe Scalloped "Cookie / Flower" badge shape.
 */
fun scallopedCookieShape(lobes: Int = 12, waveDepthFraction: Float = 0.09f): Shape =
    GenericShape { size: Size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = min(cx, cy) * (1f - waveDepthFraction)
        val amplitude = min(cx, cy) * waveDepthFraction
        val steps = 180

        for (i in 0..steps) {
            val angle = (2.0 * PI * i) / steps
            val r = baseRadius + amplitude * cos(lobes * angle).toFloat()
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/**
 * Material 3 Expressive 4-lobe Clover / Soft Starburst shape for avatars and focus badges.
 */
val ExpressiveCloverShape: Shape = scallopedCookieShape(lobes = 4, waveDepthFraction = 0.11f)

/**
 * Material 3 Expressive 10-lobe Seal / Cookie shape for active timer badges.
 */
val ExpressiveSealShape: Shape = scallopedCookieShape(lobes = 10, waveDepthFraction = 0.08f)

/**
 * Material 3 Expressive Grouped List Card Shape:
 * Top item has 36.dp top corners & 10.dp inner corners;
 * Middle items have 10.dp tight interlocking corners;
 * Bottom item has 10.dp top corners & 36.dp bottom corners.
 */
fun expressiveGroupedCardShape(
    index: Int,
    totalCount: Int,
    outerRadius: Dp = 36.dp,
    innerRadius: Dp = 10.dp
): RoundedCornerShape {
    if (totalCount <= 1) return RoundedCornerShape(outerRadius)
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        totalCount - 1 -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }
}
