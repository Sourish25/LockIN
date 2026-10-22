package com.example.ui.theme

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Material 3 Expressive Tactile & Acoustic Micro-Feedback Engine.
 * Synthesizes clean, zero-latency UI micro-sounds (slot reel ticks, morph pops,
 * lock-in chords, and percussive block thuds) paired with multi-primitive Android haptics.
 */
object ExpressiveFeedbackEngine {

    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val queuedAudioTasks = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    var isSoundEnabled: Boolean = true

    /**
     * Crisp mechanical slot-machine reel tick as digits roll up or down.
     */
    fun playSlotTick(context: Context, pitchMultiplier: Float = 1f) {
        triggerTickHaptic(context, amplitude = 95, durationMs = 9L)
        if (!isSoundEnabled) return
        val baseFreq = (680f * pitchMultiplier.coerceIn(0.75f, 1.45f)).toDouble()
        playSynthesizedBurst(
            durationMs = 18,
            generator = { t, progress ->
                val env = exp(-progress * 11.0)
                val tone = sin(2.0 * PI * baseFreq * t) * 0.7 +
                        sin(2.0 * PI * (baseFreq * 2.15) * t) * 0.3
                (tone * env * 0.28).coerceIn(-1.0, 1.0)
            }
        )
    }

    /**
     * Soft resonant pop when tapping duration pills, navigation pills, or quick chips.
     */
    fun playPillTap(context: Context) {
        triggerTickHaptic(context, amplitude = 135, durationMs = 14L)
        if (!isSoundEnabled) return
        playSynthesizedBurst(
            durationMs = 34,
            generator = { t, progress ->
                val freq = 420.0 + (240.0 * progress)
                val env = exp(-progress * 7.5)
                (sin(2.0 * PI * freq * t) * env * 0.32).coerceIn(-1.0, 1.0)
            }
        )
    }

    /**
     * Rising 2-note harmonic lock-in confirmation chord when starting Focus Lock or saving a limit.
     */
    fun playLockActivated(context: Context) {
        triggerPatternHaptic(
            context = context,
            timings = longArrayOf(0L, 18L, 45L, 32L),
            amplitudes = intArrayOf(0, 150, 0, 255)
        )
        if (!isSoundEnabled) return
        playSynthesizedBurst(
            durationMs = 155,
            generator = { t, progress ->
                val freq = if (progress < 0.45) 523.25 else 783.99 // C5 -> G5 fifth
                val localProgress = if (progress < 0.45) progress / 0.45 else (progress - 0.45) / 0.55
                val env = exp(-localProgress * 4.5)
                val harmonic = sin(2.0 * PI * freq * t) * 0.75 +
                        sin(2.0 * PI * (freq * 1.5) * t) * 0.25
                (harmonic * env * 0.35).coerceIn(-1.0, 1.0)
            }
        )
    }

    /**
     * Expressive percussive double-thud & warning pulse when a locked app is intercepted
     * and the Material 3 Expressive Red Cross Rain cascades down.
     */
    fun playInterceptionRejected(context: Context) {
        triggerPatternHaptic(
            context = context,
            timings = longArrayOf(0L, 28L, 55L, 42L, 45L, 22L),
            amplitudes = intArrayOf(0, 255, 0, 210, 0, 140)
        )
        if (!isSoundEnabled) return
        playSynthesizedBurst(
            durationMs = 190,
            generator = { t, progress ->
                val isFirstHit = progress < 0.42
                val localProgress = if (isFirstHit) progress / 0.42 else (progress - 0.42) / 0.58
                val baseFreq = if (isFirstHit) 210.0 else 155.0
                val pitchDrop = baseFreq * (1.0 - 0.28 * localProgress)
                val env = exp(-localProgress * 5.2)
                val wave = sin(2.0 * PI * pitchDrop * t) * 0.8 +
                        sin(2.0 * PI * (pitchDrop * 2.0) * t) * 0.2
                (wave * env * 0.44).coerceIn(-1.0, 1.0)
            }
        )
    }

    private fun triggerTickHaptic(context: Context, amplitude: Int, durationMs: Long) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        (amplitude / 255f).coerceIn(0.25f, 1f)
                    )
                    .compose()
                vibrator.vibrate(composition)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun triggerPatternHaptic(
        context: Context,
        timings: LongArray,
        amplitudes: IntArray
    ) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35L)
            }
        } catch (_: Exception) {
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun playSynthesizedBurst(
        durationMs: Int,
        generator: (timeSeconds: Double, normalizedProgress: Double) -> Double
    ) {
        if (queuedAudioTasks.get() >= 3) return
        queuedAudioTasks.incrementAndGet()
        audioExecutor.execute {
            var track: AudioTrack? = null
            try {
                val sampleRate = 24000
                val numSamples = (sampleRate * durationMs) / 1000
                if (numSamples <= 0) return@execute

                val pcm = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate.toDouble()
                    val progress = i.toDouble() / numSamples.toDouble()
                    // Smooth 2ms attack to eliminate speaker click artifacts
                    val attack = (i.toDouble() / 48.0).coerceAtMost(1.0)
                    val sample = generator(t, progress) * attack
                    pcm[i] = (sample * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }

                val byteCount = numSamples * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount.coerceAtLeast(1024))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, numSamples)
                track.play()
                Thread.sleep((durationMs + 25).toLong())
            } catch (_: Throwable) {
            } finally {
                queuedAudioTasks.decrementAndGet()
                try {
                    track?.stop()
                } catch (_: Throwable) {
                }
                try {
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }
}
