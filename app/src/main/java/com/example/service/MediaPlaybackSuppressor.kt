package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System media and audio focus suppression engine.
 * Instantly halts background audio playback and freezes video in Picture-in-Picture (PiP)
 * for blocked applications (YouTube, Spotify, Chrome, Netflix, etc.) during Focus Lock
 * or daily screen time limit enforcement.
 */
object MediaPlaybackSuppressor {

    private const val TAG = "MediaSuppressor"
    private val isHoldingFocus = AtomicBoolean(false)
    private var activeFocusRequest: AudioFocusRequest? = null

    private val legacyFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            Log.d(TAG, "Audio focus lost while suppression active; maintaining barrier priority")
        }
    }

    /**
     * Instantly mutes and pauses media playback for the specified blocked package.
     */
    fun suppressPlayback(context: Context, blockedPackageName: String?) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            // 1. Forceful Audio Focus Steal (Instant Video & Audio Pause)
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()

                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener(legacyFocusChangeListener)
                        .build()

                    val result = audioManager.requestAudioFocus(focusRequest)
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        activeFocusRequest = focusRequest
                        isHoldingFocus.set(true)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val result = audioManager.requestAudioFocus(
                        legacyFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        isHoldingFocus.set(true)
                    }
                }

                // 2. Dispatch hardware media pause event to system media session dispatcher
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(downEvent)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }

            // 3. Kill background audio playback services for the blocked package
            if (!blockedPackageName.isNullOrBlank()) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                activityManager?.killBackgroundProcesses(blockedPackageName)
                Log.i(TAG, "Suppressed media and killed background processes for: $blockedPackageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error suppressing media playback", e)
        }
    }

    /**
     * Cleanly releases audio focus so the pinned app or home screen can play legitimate audio.
     */
    fun releaseSuppression(context: Context) {
        if (!isHoldingFocus.get()) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                    activeFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(legacyFocusChangeListener)
                }
            }
            Log.i(TAG, "Released audio focus suppression")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio focus suppression", e)
        } finally {
            isHoldingFocus.set(false)
        }
    }
}
