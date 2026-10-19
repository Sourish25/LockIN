package com.example.ui.components

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gen-Z Material 3 Expressive "Rain of Red Expressive Crosses".
 * Instead of old-school emoji crosses, renders bold geometric M3 Expressive red crosses:
 *  - Style 0: Chunky Rounded Pill X-Charm with a contrasting core dot
 *  - Style 1: 4-Pointed Pinched Starburst Cross (organic concave-waist X)
 *  - Style 2: Asymmetric Streetwear Squircle Badge with cutout X
 */
private data class ExpressiveCrossParticle(
    val id: Int,
    var xFraction: Float,
    var yFraction: Float,
    val speedYPerSec: Float,
    val driftAmplitude: Float,
    val driftFreq: Float,
    val phaseOffset: Float,
    val sizeDp: Float,
    var rotationDeg: Float,
    val rotationSpeedDegPerSec: Float,
    val styleVariant: Int,
    val colorIndex: Int,
    val alpha: Float
)

private val ExpressiveCrimsonPalette = listOf(
    Color(0xFFFF5449), // M3 Expressive Crimson
    Color(0xFFFF897D), // M3 Coral Red
    Color(0xFFFFB4AB), // M3 Soft Rose Error
    Color(0xFFBA1A1A), // Deep Velvet Crimson
    Color(0xFFFFDAD6)  // High-contrast Blush Accent
)

private val ExpressiveCoreDotPalette = listOf(
    Color(0xFF410002),
    Color(0xFF690005),
    Color(0xFF2D0A0E)
)

@Composable
fun ExpressiveRedCrossRain(
    modifier: Modifier = Modifier,
    burstKey: Int = 0,
    particleCount: Int = 26
) {
    val particles = remember(particleCount) {
        val rng = Random(42)
        List(particleCount) { index ->
            // Deterministic initial positions so screenshots and first frames immediately look lush & full
            val row = index / 4
            val col = index % 4
            ExpressiveCrossParticle(
                id = index,
                xFraction = ((col + 0.2f + rng.nextFloat() * 0.6f) / 4f).coerceIn(0.05f, 0.95f),
                yFraction = ((row * 0.16f + rng.nextFloat() * 0.12f) % 1.15f) - 0.1f,
                speedYPerSec = 0.24f + rng.nextFloat() * 0.32f,
                driftAmplitude = 0.018f + rng.nextFloat() * 0.028f,
                driftFreq = 1.4f + rng.nextFloat() * 1.8f,
                phaseOffset = rng.nextFloat() * 6.28f,
                sizeDp = 22f + rng.nextFloat() * 28f,
                rotationDeg = rng.nextFloat() * 360f,
                rotationSpeedDegPerSec = (if (index % 2 == 0) 1f else -1f) * (38f + rng.nextFloat() * 72f),
                styleVariant = index % 3,
                colorIndex = index % ExpressiveCrimsonPalette.size,
                alpha = 0.22f + (rng.nextFloat() * 0.34f)
            )
        }
    }

    var frameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(burstKey) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dtSec = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.06f)
                    val boost = if (frameTimeMs < 700L) 1.65f else 1f
                    particles.forEach { p ->
                        p.yFraction += p.speedYPerSec * dtSec * boost
                        p.rotationDeg = (p.rotationDeg + p.rotationSpeedDegPerSec * dtSec * boost) % 360f
                        if (p.yFraction > 1.12f) {
                            p.yFraction = -0.10f
                        }
                    }
                    frameTimeMs += (dtSec * 1000f).toLong().coerceAtLeast(1L)
                }
                lastNanos = nanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Reference frameTimeMs so Compose redraws on each animation frame
        val timeSec = frameTimeMs / 1000f
        val widthPx = size.width
        val heightPx = size.height
        if (widthPx <= 0f || heightPx <= 0f) return@Canvas

        particles.forEach { p ->
            val driftX = sin(timeSec * p.driftFreq + p.phaseOffset) * p.driftAmplitude * widthPx
            val cx = (p.xFraction * widthPx + driftX).coerceIn(0f, widthPx)
            val cy = p.yFraction * heightPx
            val radiusPx = (p.sizeDp * density) * 0.5f
            val primaryColor = ExpressiveCrimsonPalette[p.colorIndex].copy(alpha = p.alpha)
            val coreColor = ExpressiveCoreDotPalette[p.id % ExpressiveCoreDotPalette.size]
                .copy(alpha = (p.alpha * 1.15f).coerceAtMost(0.75f))

            withTransform({
                translate(left = cx, top = cy)
                rotate(degrees = p.rotationDeg, pivot = Offset.Zero)
            }) {
                when (p.styleVariant) {
                    0 -> drawChunkyPillCross(radiusPx, primaryColor, coreColor)
                    1 -> drawPinchedStarburstCross(radiusPx, primaryColor, coreColor)
                    else -> drawSquircleBadgeCross(radiusPx, primaryColor, coreColor)
                }
            }
        }
    }
}

private fun DrawScope.drawChunkyPillCross(
    radius: Float,
    color: Color,
    coreColor: Color
) {
    val armLength = radius * 2.0f
    val armThickness = radius * 0.68f
    val corner = CornerRadius(armThickness * 0.5f, armThickness * 0.5f)

    withTransform({ rotate(45f, Offset.Zero) }) {
        drawRoundRect(
            color = color,
            topLeft = Offset(-armLength * 0.5f, -armThickness * 0.5f),
            size = Size(armLength, armThickness),
            cornerRadius = corner
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(-armThickness * 0.5f, -armLength * 0.5f),
            size = Size(armThickness, armLength),
            cornerRadius = corner
        )
    }

    drawCircle(
        color = coreColor,
        radius = armThickness * 0.24f,
        center = Offset.Zero
    )
}

private fun DrawScope.drawPinchedStarburstCross(
    radius: Float,
    color: Color,
    coreColor: Color
) {
    val path = Path()
    val steps = 48
    for (i in 0..steps) {
        val theta = (i.toDouble() / steps.toDouble()) * 2.0 * Math.PI
        // 4 lobes oriented at 45°, 135°, 225°, 315° for an expressive pinched X silhouette
        val wave = cos(4.0 * (theta - Math.PI / 4.0)).toFloat()
        val r = radius * (0.64f + 0.36f * wave)
        val x = r * cos(theta).toFloat()
        val y = r * sin(theta).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
    drawCircle(color = coreColor, radius = radius * 0.20f, center = Offset.Zero)
}

private fun DrawScope.drawSquircleBadgeCross(
    radius: Float,
    color: Color,
    coreColor: Color
) {
    val boxSize = radius * 1.85f
    drawRoundRect(
        color = color,
        topLeft = Offset(-boxSize * 0.5f, -boxSize * 0.5f),
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(radius * 0.58f, radius * 0.58f)
    )
    // Inner contrasting X
    val innerArmLen = radius * 1.15f
    val innerThick = radius * 0.34f
    val innerCorner = CornerRadius(innerThick * 0.5f, innerThick * 0.5f)
    withTransform({ rotate(45f, Offset.Zero) }) {
        drawRoundRect(
            color = coreColor,
            topLeft = Offset(-innerArmLen * 0.5f, -innerThick * 0.5f),
            size = Size(innerArmLen, innerThick),
            cornerRadius = innerCorner
        )
        drawRoundRect(
            color = coreColor,
            topLeft = Offset(-innerThick * 0.5f, -innerArmLen * 0.5f),
            size = Size(innerThick, innerArmLen),
            cornerRadius = innerCorner
        )
    }
}

/**
 * Native hardware-accelerated View for `LockWindowOverlayManager` so the system window barrier
 * rains Material 3 Expressive red crosses at 60fps whenever a locked app is intercepted.
 */
class ExpressiveCrossRainOverlayView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rectF = RectF()
    private val starPath = AndroidPath()

    private val paletteInts = intArrayOf(
        AndroidColor.parseColor("#FF5449"),
        AndroidColor.parseColor("#FF897D"),
        AndroidColor.parseColor("#FFB4AB"),
        AndroidColor.parseColor("#BA1A1A"),
        AndroidColor.parseColor("#FFDAD6")
    )
    private val coreInts = intArrayOf(
        AndroidColor.parseColor("#410002"),
        AndroidColor.parseColor("#690005"),
        AndroidColor.parseColor("#2D0A0E")
    )

    private val particles = List(24) { index ->
        val rng = Random(index * 31 + 7)
        val row = index / 4
        val col = index % 4
        ExpressiveCrossParticle(
            id = index,
            xFraction = ((col + 0.2f + rng.nextFloat() * 0.6f) / 4f).coerceIn(0.05f, 0.95f),
            yFraction = ((row * 0.17f + rng.nextFloat() * 0.12f) % 1.15f) - 0.1f,
            speedYPerSec = 0.26f + rng.nextFloat() * 0.34f,
            driftAmplitude = 0.02f + rng.nextFloat() * 0.025f,
            driftFreq = 1.5f + rng.nextFloat() * 1.6f,
            phaseOffset = rng.nextFloat() * 6.28f,
            sizeDp = 22f + rng.nextFloat() * 28f,
            rotationDeg = rng.nextFloat() * 360f,
            rotationSpeedDegPerSec = (if (index % 2 == 0) 1f else -1f) * (42f + rng.nextFloat() * 68f),
            styleVariant = index % 3,
            colorIndex = index % paletteInts.size,
            alpha = 0.26f + rng.nextFloat() * 0.34f
        )
    }

    private var lastFrameMs = SystemClock.uptimeMillis()
    private val startMs = SystemClock.uptimeMillis()

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dtSec = ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.06f)
        lastFrameMs = now
        val elapsedSec = (now - startMs) / 1000f
        val boost = if (elapsedSec < 0.8f) 1.75f else 1f

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (p in particles) {
            p.yFraction += p.speedYPerSec * dtSec * boost
            p.rotationDeg = (p.rotationDeg + p.rotationSpeedDegPerSec * dtSec * boost) % 360f
            if (p.yFraction > 1.12f) {
                p.yFraction = -0.10f
            }

            val driftX = sin(elapsedSec * p.driftFreq + p.phaseOffset) * p.driftAmplitude * w
            val cx = (p.xFraction * w + driftX).coerceIn(0f, w)
            val cy = p.yFraction * h
            val radius = (p.sizeDp * density) * 0.5f

            val baseColor = paletteInts[p.colorIndex]
            val alphaInt = (p.alpha * 255f).toInt().coerceIn(30, 210)
            val coreColor = coreInts[p.id % coreInts.size]

            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(p.rotationDeg)

            when (p.styleVariant) {
                0 -> {
                    paint.color = baseColor
                    paint.alpha = alphaInt
                    val armLen = radius * 2.0f
                    val armThick = radius * 0.68f
                    val r = armThick * 0.5f
                    canvas.save()
                    canvas.rotate(45f)
                    rectF.set(-armLen * 0.5f, -armThick * 0.5f, armLen * 0.5f, armThick * 0.5f)
                    canvas.drawRoundRect(rectF, r, r, paint)
                    rectF.set(-armThick * 0.5f, -armLen * 0.5f, armThick * 0.5f, armLen * 0.5f)
                    canvas.drawRoundRect(rectF, r, r, paint)
                    canvas.restore()

                    paint.color = coreColor
                    paint.alpha = (alphaInt * 1.15f).toInt().coerceAtMost(220)
                    canvas.drawCircle(0f, 0f, armThick * 0.24f, paint)
                }
                1 -> {
                    starPath.reset()
                    val steps = 40
                    for (i in 0..steps) {
                        val theta = (i.toDouble() / steps.toDouble()) * 2.0 * Math.PI
                        val wave = cos(4.0 * (theta - Math.PI / 4.0)).toFloat()
                        val rad = radius * (0.64f + 0.36f * wave)
                        val x = rad * cos(theta).toFloat()
                        val y = rad * sin(theta).toFloat()
                        if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
                    }
                    starPath.close()
                    paint.color = baseColor
                    paint.alpha = alphaInt
                    canvas.drawPath(starPath, paint)

                    paint.color = coreColor
                    paint.alpha = (alphaInt * 1.15f).toInt().coerceAtMost(220)
                    canvas.drawCircle(0f, 0f, radius * 0.20f, paint)
                }
                else -> {
                    val boxSize = radius * 1.85f
                    val boxR = radius * 0.58f
                    paint.color = baseColor
                    paint.alpha = alphaInt
                    rectF.set(-boxSize * 0.5f, -boxSize * 0.5f, boxSize * 0.5f, boxSize * 0.5f)
                    canvas.drawRoundRect(rectF, boxR, boxR, paint)

                    val innerLen = radius * 1.15f
                    val innerThick = radius * 0.34f
                    val ir = innerThick * 0.5f
                    paint.color = coreColor
                    paint.alpha = (alphaInt * 1.2f).toInt().coerceAtMost(235)
                    canvas.save()
                    canvas.rotate(45f)
                    rectF.set(-innerLen * 0.5f, -innerThick * 0.5f, innerLen * 0.5f, innerThick * 0.5f)
                    canvas.drawRoundRect(rectF, ir, ir, paint)
                    rectF.set(-innerThick * 0.5f, -innerLen * 0.5f, innerThick * 0.5f, innerLen * 0.5f)
                    canvas.drawRoundRect(rectF, ir, ir, paint)
                    canvas.restore()
                }
            }

            canvas.restore()
        }

        if (isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }
}
