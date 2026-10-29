

/**
 * Package: com.jmd.autotext.ui.activity
 * Module: UI Calling Simulator Layer
 *
 * Fullscreen incoming call simulator activity playing real ringtones and vibration patterns,
 * displaying custom caller name and number, and supporting Accept/Decline interactions.
 *
 * Linked With:
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Screen: `FakeCallScreen`
 */
package com.jmd.autotext.ui.activity

import android.app.KeyguardManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.jmd.autotext.ui.theme.autoTextTheme
import com.jmd.autotext.ui.screen.fakeCallScreen

/**
 * Activity rendering incoming call simulation screen with audio and haptic feedback.
 */
class FakeCallActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acquire wake lock to ensure CPU remains active during incoming call simulation
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AutoText:FakeCallWakeLock",
            )
            wakeLock?.acquire(30000L)
        } catch (e: Exception) {
            Log.w("FakeCallActivity", "WakeLock: ${e.message}")
        }

        // Show over lockscreen and turn screen on (minSdk is 26, O_MR1 is 27)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager?.requestDismissKeyguard(this, null)

        val callerName = intent.getStringExtra("caller_name") ?: "Incoming Call"
        val callerNumber = intent.getStringExtra("caller_number") ?: "+1 (555) 019-2834"
        val callerStyle = intent.getStringExtra("caller_style") ?: "PIXEL"

        startRinging()

        setContent {
            autoTextTheme {
                fakeCallScreen(
                    name = callerName,
                    number = callerNumber,
                    style = callerStyle,
                    onAnswer = { stopRinging() },
                ) {
                    stopRinging()
                    finish()
                }
            }
        }
    }

    /**
     * Initializes media player with default system ringtone and activates repetitive haptic vibration motor.
     */
    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer.create(this, ringtoneUri).apply {
                isLooping = true
                start()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            }

            val pattern = longArrayOf(0, 1000, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error starting fake call ring/vibration", e)
        }
    }

    /**
     * Silences ringtone playback and halts vibration pattern when call is answered, dismissed, or activity destroyed.
     */
    private fun stopRinging() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w("FakeCallActivity", "Error stopping ringing: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}


