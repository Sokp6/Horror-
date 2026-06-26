package com.horrorgame.awakening.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Manages sound effects and vibration for the horror atmosphere.
 * Uses programmatic sounds since we don't have audio files.
 */
class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private var isSoundEnabled = true
    private var isVibrationEnabled = true

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    fun setVibrationEnabled(enabled: Boolean) {
        isVibrationEnabled = enabled
    }

    // ==================== VIBRATION EFFECTS ====================

    fun vibrateHeartbeat() {
        if (!isVibrationEnabled) return
        val pattern = longArrayOf(0, 100, 200, 150, 200, 100)
        vibrate(pattern, -1)
    }

    fun vibrateJumpScare() {
        if (!isVibrationEnabled) return
        val pattern = longArrayOf(0, 50, 30, 80, 30, 50, 20, 200, 40, 100)
        vibrate(pattern, -1)
    }

    fun vibratePulse() {
        if (!isVibrationEnabled) return
        val pattern = longArrayOf(0, 200, 600, 200, 600, 200)
        vibrate(pattern, 0)
    }

    fun vibrateShort() {
        if (!isVibrationEnabled) return
        vibrate(longArrayOf(0, 100), -1)
    }

    fun stopVibration() {
        val vibrator = getVibrator() ?: return
        vibrator.cancel()
    }

    private fun vibrate(pattern: LongArray, repeat: Int) {
        val vibrator = getVibrator() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, repeat)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, repeat)
        }
    }

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        soundPool?.release()
        soundPool = null
        stopVibration()
    }
}
